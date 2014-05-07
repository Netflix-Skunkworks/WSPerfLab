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
        WSClient client = new WSClient();
        client.startMonitoring();
        client.startLoad().toBlockingObservable().forEach(l -> {
        });
    }

    final NumerusRollingNumber counter = new NumerusRollingNumber(Events.SUCCESS, NumerusProperty.Factory.asProperty(10000), NumerusProperty.Factory.asProperty(10));
    final NumerusRollingPercentile latency = new NumerusRollingPercentile(NumerusProperty.Factory.asProperty(10000), NumerusProperty.Factory.asProperty(10), NumerusProperty.Factory.asProperty(1000), NumerusProperty.Factory.asProperty(Boolean.TRUE));

    final int INTERVAL_RATE_MILLIS = 1;
    private final Observable<?> client;
    private final HttpClient<ByteBuf, ByteBuf> httpClient;

    public WSClient() {
        httpClient = RxNetty.createHttpClient("localhost", 8080);
        client = httpClient.submit(HttpClientRequest.createGet("/?id=23452345"))
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

        Observable<Observable<Long>> stepIntervals = Observable.interval(10, TimeUnit.SECONDS).map(l -> l + 1).take(9).startWith(1L).map(step -> {
            step = 7 + step;
            long rps = step * 1000;
            long interval = TimeUnit.SECONDS.toMicros(1) / rps;
            StringBuilder str = new StringBuilder();
            str.append("########################################################################################").append("\n");
            str.append("Step: " + (step) + "  Interval: " + interval + "micros  Rate: " + rps + "/s").append("\n");
            str.append("########################################################################################").append("\n");

            System.out.println(str.toString());

            return Observable.interval(interval, TimeUnit.MICROSECONDS);
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
            long cumulativeSuccess = counter.getCumulativeSum(Events.SUCCESS);
            long cumulativeError = counter.getCumulativeSum(Events.HTTP_ERROR);
            long cumulativeNettyError = counter.getCumulativeSum(Events.NETTY_ERROR);

            long rollingSuccess = counter.getRollingSum(Events.SUCCESS);
            if (rollingSuccess > 0) {
                rollingSuccess = rollingSuccess / 10;
            }
            long rollingError = counter.getRollingSum(Events.HTTP_ERROR);
            if (rollingError > 0) {
                rollingError = rollingError / 10;
            }
            long rollingNettyError = counter.getRollingSum(Events.NETTY_ERROR);
            if (rollingNettyError > 0) {
                rollingNettyError = rollingError / 10;
            }

            System.out.println("Total => Success: " + cumulativeSuccess + "  Error: " + cumulativeError + "  Netty Error: " + cumulativeNettyError +
                    "   Last 10s => Success: " + rollingSuccess + "/s  Error: " + rollingError + "/s " + "  Netty Error: " + rollingNettyError + "/s " +
                    "   Latency => 50th: " + latency.getPercentile(50.0) + "  90th: " + latency.getPercentile(90.0)
                    + "  99th: " + latency.getPercentile(99.0) + "  100th: " + latency.getPercentile(100.0));

            System.out.println("     Netty => Used: " + httpClient.getStats().getInUseCount() + "  Idle: " + httpClient.getStats().getIdleCount() +
                    "  Total Conns: " + httpClient.getStats().getTotalConnectionCount() +
                    "  AcqReq: " + httpClient.getStats().getPendingAcquireRequestCount() +
                    "  RelReq: " + httpClient.getStats().getPendingReleaseRequestCount());

        }).subscribe();
    }

}
