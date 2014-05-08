package perf.test.rxnetty;

import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import rx.Observable;

import java.util.concurrent.TimeUnit;

import static com.netflix.numerus.NumerusProperty.Factory.asProperty;

public final class StartServer {

    static final int rollingSeconds = 5;

    static final NumerusRollingNumber counter = new NumerusRollingNumber(CounterEvent.SUCCESS,
                                                                         asProperty( rollingSeconds * 1000), asProperty(10));
    static final NumerusRollingPercentile latency = new NumerusRollingPercentile(asProperty(rollingSeconds * 1000),
                                                                                 asProperty(10),
                                                                                 asProperty(1000), asProperty(Boolean.TRUE));

    public static void main(String[] args) {
        int port = 8888;
        String backendHost = "localhost";
        int backendPort = 8989;
        if (args.length == 0) {
            // use defaults
        } else if (args.length == 3) {
            port = Integer.parseInt(args[0]);
            backendHost = args[1];
            backendPort = Integer.parseInt(args[2]);
        } else {
            System.err.println("Execute with either no argument (for defaults) or 3 arguments: HOST, BACKEND_HOST, BACKEND_PORT");
            System.exit(-1);
        }

        TestRouteBasic route = new TestRouteBasic(backendHost, backendPort);

        System.out.println("Starting service on port " + port + " with backend at " + backendHost + ':' + backendPort + " ...");
        startMonitoring();
        RxNetty.createHttpServer(port, (request, response) -> {
            try {
                long startTime = System.currentTimeMillis();
                counter.increment(CounterEvent.REQUESTS);
                return route.handle(request, response)
                            .doOnCompleted(() ->  {
                                counter.increment(CounterEvent.SUCCESS);
                                latency.addValue((int)(System.currentTimeMillis() - startTime));
                            })
                            .doOnError(t -> counter.increment(CounterEvent.NETTY_ERROR));
            } catch (Throwable e) {
                System.err.println("Server => Error [" + request.getPath() + "] => " + e);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                return response.writeStringAndFlush("Error 500: Bad Request\n" + e.getMessage() + '\n');
            }
        }).startAndWait();
    }

    private static void startMonitoring() {
        Observable.interval(5, TimeUnit.SECONDS).doOnNext(l -> {
            StringBuilder msg = new StringBuilder();
            msg.append("Total => ");
            msg.append("  Requests: ").append(counter.getCumulativeSum(CounterEvent.REQUESTS));
            msg.append("  Success: ").append(counter.getCumulativeSum(CounterEvent.SUCCESS));
            msg.append("  Error: ").append(counter.getCumulativeSum(CounterEvent.HTTP_ERROR));
            msg.append("  Netty Error: ").append(counter.getCumulativeSum(CounterEvent.NETTY_ERROR));
            msg.append("  Bytes: ").append(counter.getCumulativeSum(CounterEvent.BYTES) / 1024).append("kb");
            msg.append("    Rolling =>");
            msg.append("  Success: ").append(getRollingSum(CounterEvent.SUCCESS)).append("/s");
            msg.append("  Error: ").append(getRollingSum(CounterEvent.HTTP_ERROR)).append("/s");
            msg.append("  Netty Error: ").append(getRollingSum(CounterEvent.NETTY_ERROR)).append("/s");
            msg.append("  Bytes: ").append(getRollingSum(CounterEvent.BYTES) / 1024).append("kb/s");
            msg.append("    Latency (ms) => 50th: ").append(latency.getPercentile(50.0)).append(
                    "  90th: ").append(latency.getPercentile(90.0));
            msg.append("  99th: ").append(latency.getPercentile(99.0)).append("  100th: ").append(latency.getPercentile(
                    100.0));
            System.out.println(msg.toString());
        }).subscribe();
    }

    private static long getRollingSum(CounterEvent e) {
        long s = counter.getRollingSum(e);
        if (s > 0) {
            s /= rollingSeconds;
        }
        return s;
    }
}
