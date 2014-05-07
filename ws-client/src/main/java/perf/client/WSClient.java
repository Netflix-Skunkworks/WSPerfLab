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
    final int PORT = 8888;
    final String QUERY = "/?id=23452345";
    final int STEP_DURATION = 30; // seconds
    final int FIRST_STEP = 1; // starting point (1 == 1000rps, 2 == 2000rps)

    final int ROLLING_SECONDS = 5;
    final NumerusRollingNumber counter = new NumerusRollingNumber(Events.SUCCESS, NumerusProperty.Factory.asProperty(ROLLING_SECONDS * 1000), NumerusProperty.Factory.asProperty(10));
    final NumerusRollingPercentile latency = new NumerusRollingPercentile(NumerusProperty.Factory.asProperty(ROLLING_SECONDS * 1000), NumerusProperty.Factory.asProperty(10), NumerusProperty.Factory.asProperty(1000), NumerusProperty.Factory.asProperty(Boolean.TRUE));

    private final Observable<?> client;
    private final HttpClient<ByteBuf, ByteBuf> httpClient;

    public WSClient() {
        httpClient = RxNetty.createHttpClient(HOST, PORT);
        client = httpClient.submit(HttpClientRequest.createGet(QUERY))
                .flatMap((response) -> {
                    if (response.getStatus().code() == 200) {
                        counter.increment(Events.SUCCESS);
                    } else {
                        counter.increment(Events.HTTP_ERROR);
                    }
                    return response.getContent();
                }).onErrorResumeNext((t) -> {
                    counter.increment(Events.NETTY_ERROR);
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

                    return Observable.timer(0, interval, TimeUnit.MICROSECONDS);
                });

        return Observable.switchOnNext(stepIntervals).doOnEach((n) -> {
            long startTime = System.currentTimeMillis();
            client.doOnTerminate(() -> {
                latency.addValue((int) (System.currentTimeMillis() - startTime));
            }).subscribe();
        });
    }

    private void startMonitoring() {
        Observable.interval(5, TimeUnit.SECONDS).doOnNext(l -> {
            StringBuilder msg = new StringBuilder();
            msg.append("Total => ");
            msg.append("  Success: ").append(counter.getCumulativeSum(Events.SUCCESS));
            msg.append("  Error: ").append(counter.getCumulativeSum(Events.HTTP_ERROR));
            msg.append("  Netty Error: ").append(counter.getCumulativeSum(Events.NETTY_ERROR));
            msg.append("    Rolling =>");
            msg.append("  Success: ").append(getRollingSum(Events.SUCCESS)).append("/s");
            msg.append("  Error: ").append(getRollingSum(Events.HTTP_ERROR)).append("/s");
            msg.append("  Netty Error: ").append(getRollingSum(Events.NETTY_ERROR)).append("/s");
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

    private long getRollingSum(Events e) {
        long s = counter.getRollingSum(e);
        if (s > 0) {
            s = s / ROLLING_SECONDS;
        }
        return s;
    }

}
