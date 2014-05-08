package perf.client;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;

import java.util.concurrent.TimeUnit;

import rx.Observable;

import com.netflix.numerus.NumerusProperty;
import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;

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

    private final Observable<?> client;
    private final HttpClient<ByteBuf, ByteBuf> httpClient;

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

        httpClient = RxNetty.createHttpClient(this.host, this.port);
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
                    counter.increment(CounterEvent.NETTY_ERROR);
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
                }).subscribe();
            }
        });
    }

    private void startMonitoring() {
        Observable.interval(5, TimeUnit.SECONDS).doOnNext(l -> {
            StringBuilder msg = new StringBuilder();
            msg.append("Total => ");
            msg.append("  Success: ").append(counter.getCumulativeSum(CounterEvent.SUCCESS));
            msg.append("  Error: ").append(counter.getCumulativeSum(CounterEvent.HTTP_ERROR));
            msg.append("  Netty Error: ").append(counter.getCumulativeSum(CounterEvent.NETTY_ERROR));
            msg.append("  Bytes: ").append(counter.getCumulativeSum(CounterEvent.BYTES) / 1024).append("kb");
            msg.append("    Rolling =>");
            msg.append("  Success: ").append(getRollingSum(CounterEvent.SUCCESS)).append("/s");
            msg.append("  Error: ").append(getRollingSum(CounterEvent.HTTP_ERROR)).append("/s");
            msg.append("  Netty Error: ").append(getRollingSum(CounterEvent.NETTY_ERROR)).append("/s");
            msg.append("  Bytes: ").append(getRollingSum(CounterEvent.BYTES) / 1024).append("kb/s");
            msg.append("    Latency (ms) => 50th: ").append(latency.getPercentile(50.0)).append("  90th: ").append(latency.getPercentile(90.0));
            msg.append("  99th: ").append(latency.getPercentile(99.0)).append("  100th: ").append(latency.getPercentile(100.0));
            System.out.println(msg.toString());

            StringBuilder n = new StringBuilder();
            n.append("     Netty => Used: ").append(httpClient.getStats().getInUseCount());
            n.append("  Idle: ").append(httpClient.getStats().getIdleCount());
            n.append("  Total Conns: ").append(httpClient.getStats().getTotalConnectionCount());
            n.append("  AcqReq: ").append(httpClient.getStats().getPendingAcquireRequestCount());
            n.append("  RelReq: ").append(httpClient.getStats().getPendingReleaseRequestCount());
            System.out.println(n.toString());
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
