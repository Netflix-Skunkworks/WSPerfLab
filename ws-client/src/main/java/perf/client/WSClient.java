package perf.client;

import io.reactivex.netty.RxNetty;
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

    final int NUM_CORES = 1;
    final int INTERVAL_RATE_MILLIS = 1;
    private final Observable<?> client;

    public WSClient() {
        client = RxNetty.createHttpClient("localhost", 8080)
                .submit(HttpClientRequest.createGet("/?id=23452345"))
                .flatMap((response) -> {
                    if (response.getStatus().code() == 200) {
                        counter.increment(Events.SUCCESS);
                    } else {
                        counter.increment(Events.ERROR);
                    }
                    return response.getContent();
                });
    }

    private final int[] rps_per_thread = new int[] { 50, 100, 250, 500, 750, 1000, 1500, 2000, 2500, 3000 };

    public Observable<Long> startLoad() {

        Observable<Observable<Long>> stepIntervals = Observable.interval(30, TimeUnit.SECONDS).map(l -> l + 1).take(9).startWith(0L).map(step -> {
            long interval = TimeUnit.SECONDS.toMicros(1) / rps_per_thread[step.intValue()];
            StringBuilder str = new StringBuilder();
            str.append("###############################################################################").append("\n");
            str.append("Step: " + (step + 1) + "  Interval: " + interval + "micros" + "  Rate: " + rps_per_thread[step.intValue()] + "/s").append("\n");
            str.append("###############################################################################").append("\n");

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
            long cumulativeError = counter.getCumulativeSum(Events.ERROR);

            long rollingSuccess = counter.getRollingSum(Events.SUCCESS);
            if (rollingSuccess > 0) {
                rollingSuccess = rollingSuccess / 10;
            }
            long rollingError = counter.getRollingSum(Events.ERROR);
            if (rollingError > 0) {
                rollingError = rollingError / 10;
            }

            System.out.println("Total => Success: " + cumulativeSuccess + " Error: " + cumulativeError +
                    "   Last 10s => Success: " + rollingSuccess + "/s Error: " + rollingError + "/s " +
                    "   Latency => 50th: " + latency.getPercentile(50.0) + "  90th: " + latency.getPercentile(90.0)
                    + "  99th: " + latency.getPercentile(99.0) + "  100th: " + latency.getPercentile(100.0));
        }).subscribe();
    }

}
