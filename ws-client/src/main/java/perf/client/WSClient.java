package perf.client;

import com.netflix.numerus.NumerusProperty;
import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.client.PoolExhaustedException;
import io.reactivex.netty.client.PoolStats;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.codehaus.jackson.map.ObjectMapper;
import rx.Observable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WSClient {

    public static void main(String[] args) {
        WSClient client = null;
        if (args.length == 0) {
            client = new WSClient();
        } else {
            try {
                String host = args[0];
                int port = 8989;
                if (args.length > 1) {
                    port = Integer.parseInt(args[1]);
                }
                int firstStep = 1;
                if (args.length > 2) {
                    firstStep = Integer.parseInt(args[2]);
                }
                int duration = 30;
                if (args.length > 3) {
                    duration = Integer.parseInt(args[3]);
                }
                String query = "/?id=12345";
                if (args.length > 4) {
                    query = args[4];
                }
                client = new WSClient(host, port, firstStep, duration, query);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        client.startMonitoring();
        client.startLoad().toBlockingObservable().last();
    }

    final String host;
    final int port;
    final String query;
    final int stepDuration; // seconds
    final int firstStep; // starting point (1 == 1000rps, 2 == 2000rps)

    static final int rollingSeconds = 5;

    final NumerusRollingNumber counter = new NumerusRollingNumber(CounterEvent.SUCCESS, NumerusProperty.Factory.asProperty(
            rollingSeconds * 1000), NumerusProperty.Factory.asProperty(10));
    final NumerusRollingPercentile latency = new NumerusRollingPercentile(NumerusProperty.Factory.asProperty(
            rollingSeconds * 1000), NumerusProperty.Factory.asProperty(10), NumerusProperty.Factory.asProperty(1000), NumerusProperty.Factory.asProperty(Boolean.TRUE));

    private final Observable<ByteBuf> client;
    private final HttpClient<ByteBuf, ByteBuf> httpClient;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public WSClient() {
        this("localhost", 8888, 1, 30, "?id=12345");
    }

    public WSClient(String host, int port, int firstStep, int stepDuration, String query) {
        this.host = host;
        this.port = port;
        this.firstStep = firstStep;
        this.stepDuration = stepDuration;
        this.query = query;

        System.out.println("Starting client with hostname: " + host + " port: " + port + " first-step: " + firstStep + " step-duration: " + stepDuration + "s query: " + query);

        httpClient = new HttpClientBuilder<ByteBuf, ByteBuf>(this.host, this.port)
                .withMaxConnections(1000).build();
        client = httpClient.submit(HttpClientRequest.createGet(this.query))
                .flatMap(response -> {
                    if (response.getStatus().code() == 200) {
                        counter.increment(CounterEvent.SUCCESS);
                    } else {
                        counter.increment(CounterEvent.HTTP_ERROR);
                    }
                    return response.getContent().doOnNext(bb -> {
                        counter.add(CounterEvent.BYTES, bb.readableBytes());
                    });
                }).doOnError((t) -> {
                    if (t instanceof PoolExhaustedException) {
                        counter.increment(CounterEvent.POOL_EXHAUSTED);
                    } else {
                        counter.increment(CounterEvent.NETTY_ERROR);
                    }
                });
    }

    public Observable<Long> startLoad() {

        Observable<Observable<Long>> stepIntervals = Observable.timer(0, stepDuration, TimeUnit.SECONDS).map(l -> l + firstStep)
                .map(step -> {
                    long rps = step * 1000;
                    long interval = TimeUnit.SECONDS.toMicros(1) / rps;
                    StringBuilder str = new StringBuilder();
                    str.append('\n');
                    str.append("########################################################################################").append(
                            '\n');
                    str.append("Step: " + step + "  Interval: " + interval + "micros  Rate: " + rps + "/s").append('\n');
                    str.append("########################################################################################").append(
                            '\n');

                    System.out.println(str.toString());

                    if (interval < 1000) {
                        /**
                         * An optimization that reduces the CPU load on the timer threads.
                         * This sacrifices more event distribution of requests for CPU by bursting requests every 100ms
                         * instead of scheduling granularly at the microsecond level.
                         *
                         * We can experiment further with 10ms/100ms intervals for the right balance.
                         */
                        int fixedInterval = 100;
                        // 1000 (1ms converted to microseconds) / interval (in microseconds) to get the number per ms * number of milliseconds
                        long numPerFixedInterval = 1000 / interval * fixedInterval;
                        return Observable.timer(0, fixedInterval, TimeUnit.MILLISECONDS).map(i -> numPerFixedInterval);
                    } else {
                        return Observable.timer(0, interval, TimeUnit.MICROSECONDS).map(i -> 1L);
                    }
                });

        return Observable.switchOnNext(stepIntervals).doOnNext(n -> {
            for (int i = 0; i < n; i++) {
                long startTime = System.currentTimeMillis();
                client.doOnCompleted(() -> {
                    // only record latency if we successfully executed
                    latency.addValue((int) (System.currentTimeMillis() - startTime));
                }).onErrorResumeNext(Observable.<ByteBuf>empty()).subscribe();
            }
        });
    }

    private void startMonitoring() {
        Observable.interval(5, TimeUnit.SECONDS).doOnNext(l -> {
            Map<String, Object> m = new HashMap<String, Object>();

            m.put("totalSuccesses", counter.getCumulativeSum(CounterEvent.SUCCESS));
            m.put("totalErrors", counter.getCumulativeSum(CounterEvent.HTTP_ERROR));
            m.put("totalNettyErrors", counter.getCumulativeSum(CounterEvent.NETTY_ERROR));
            m.put("totalBytes", counter.getCumulativeSum(CounterEvent.BYTES));

            m.put("rollingSuccess", getRollingSum(CounterEvent.SUCCESS));
            m.put("rollingErrors", getRollingSum(CounterEvent.HTTP_ERROR));
            m.put("rollingNettyErrors", getRollingSum(CounterEvent.NETTY_ERROR));
            m.put("rollingPoolExhausted", getRollingSum(CounterEvent.POOL_EXHAUSTED));
            m.put("rollingBytes", getRollingSum(CounterEvent.BYTES) / 1024);
            m.put("rollingLatencyMedian", latency.getPercentile(50.0));
            m.put("rollingLatency90", latency.getPercentile(90.0));
            m.put("rollingLatency99", latency.getPercentile(99.0));
            m.put("rollingLatencyMax", latency.getPercentile(100.0));

            PoolStats poolStats = httpClient.getStats();
            m.put("connsInUse", poolStats.getInUseCount());
            m.put("connsIdeal", poolStats.getIdleCount());
            m.put("connsTotal", poolStats.getTotalConnectionCount());
            m.put("connsPendingAcquire", poolStats.getPendingAcquireRequestCount());
            m.put("connsPendingRelease", poolStats.getPendingReleaseRequestCount());

            try {
                String statMsg = this.jsonMapper.writeValueAsString(m);
                System.out.println(statMsg);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).subscribe();
    }

    private long getRollingSum(CounterEvent e) {
        long s = counter.getRollingSum(e);
        if (s > 0) {
            s /= rollingSeconds;
        }
        return s;
    }

}
