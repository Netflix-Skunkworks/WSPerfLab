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
        // TODO add CLI arguments
        WSClient client = new WSClient();
        client.startMonitoring();
        client.startLoad().toBlockingObservable().last();
    }

    final String HOST = "localhost";
    final int PORT = 8989;
    final String QUERY = "/?id=23452345";
    final int STEP_DURATION = 30; // seconds
    final int FIRST_STEP = 1; // starting point (1 == 1000rps, 2 == 2000rps)

    final int ROLLING_SECONDS = 5;
    final NumerusRollingNumber counter = new NumerusRollingNumber(CounterEvent.SUCCESS, NumerusProperty.Factory.asProperty(ROLLING_SECONDS * 1000), NumerusProperty.Factory.asProperty(10));
    final NumerusRollingPercentile latency = new NumerusRollingPercentile(NumerusProperty.Factory.asProperty(ROLLING_SECONDS * 1000), NumerusProperty.Factory.asProperty(10), NumerusProperty.Factory.asProperty(1000), NumerusProperty.Factory.asProperty(Boolean.TRUE));

    private final Observable<?> client;
    private final HttpClient<ByteBuf, ByteBuf> httpClient;

    public WSClient() {
        httpClient = RxNetty.createHttpClient(HOST, PORT);
        client = httpClient.submit(HttpClientRequest.createGet(QUERY))
                .flatMap((response) -> {
                    if (response.getStatus().code() == 200) {
                        counter.increment(CounterEvent.SUCCESS);
                    } else {
                        counter.increment(CounterEvent.HTTP_ERROR);
                    }
                    return response.getContent().doOnNext(bb -> {
                        counter.add(CounterEvent.BYTES, bb.readableBytes());
                    });
                }).onErrorResumeNext((t) -> {
                    counter.increment(CounterEvent.NETTY_ERROR);
                    return Observable.empty();
                });
    }

    public Observable<Long> startLoad() {

        Observable<Observable<Long>> stepIntervals = Observable.timer(0, STEP_DURATION, TimeUnit.SECONDS).map(l -> l + FIRST_STEP)
                .map(step -> {
                    long rps = step * 1000;
                    long interval = TimeUnit.SECONDS.toMicros(1) / rps;
                    StringBuilder str = new StringBuilder();
                    str.append("\n");
                    str.append("########################################################################################").append("\n");
                    str.append("Step: " + step + "  Interval: " + interval + "micros  Rate: " + rps + "/s").append("\n");
                    str.append("########################################################################################").append("\n");

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
                        long numPerFixedInterval = (1000 / interval) * fixedInterval;
                        return Observable.timer(0, fixedInterval, TimeUnit.MILLISECONDS).map(i -> numPerFixedInterval);
                    } else {
                        return Observable.timer(0, interval, TimeUnit.MICROSECONDS).map(i -> 1L);
                    }
                });

        return Observable.switchOnNext(stepIntervals).doOnNext((n) -> {
            for (int i = 0; i < n; i++) {
                long startTime = System.currentTimeMillis();
                client.doOnTerminate(() -> {
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
            s = s / ROLLING_SECONDS;
        }
        return s;
    }

}
