package perf.test.rxnetty;

import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.PoolExhaustedException;
import io.reactivex.netty.protocol.http.client.HttpClient;
import perf.test.utils.JsonParseException;
import rx.Observable;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static com.netflix.numerus.NumerusProperty.Factory.asProperty;

public final class StartServer {

    static final int rollingSeconds = 5;

    static final NumerusRollingNumber counter = new NumerusRollingNumber(CounterEvent.SUCCESS,
                                                                         asProperty( rollingSeconds * 1000), asProperty(10));
    static final NumerusRollingPercentile latency = new NumerusRollingPercentile(asProperty(rollingSeconds * 1000),
                                                                                 asProperty(10),
                                                                                 asProperty(1000), asProperty(Boolean.TRUE));
    private static TestRouteBasic route;

    public static void main(String[] args) {
        int port = 8888;
        String backendHost = "127.0.0.1";
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

        route = new TestRouteBasic(backendHost, backendPort);

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
                            .onErrorResumeNext(t -> {
                                if (t instanceof PoolExhaustedException) {
                                    counter.increment(CounterEvent.CLIENT_POOL_EXHAUSTION);
                                } else if (t instanceof SocketException) {
                                    counter.increment(CounterEvent.SOCKET_EXCEPTION);
                                } else if (t instanceof IOException) {
                                    counter.increment(CounterEvent.IO_EXCEPTION);
                                } else if (t instanceof CancellationException) {
                                    counter.increment(CounterEvent.CANCELLATION_EXCEPTION);
                                } else if (t instanceof JsonParseException) {
                                    counter.increment(CounterEvent.PARSING_EXCEPTION);
                                } else {
                                    counter.increment(CounterEvent.NETTY_ERROR);
                                }
                                response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                                return Observable.empty();
                            });
            } catch (Throwable e) {
                System.err.println("Server => Error [" + request.getPath() + "] => " + e);
                counter.increment(CounterEvent.NETTY_ERROR);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                return response.writeStringAndFlush("Error 400: Bad Request\n" + e.getMessage() + '\n');
            }
        }).startAndWait();
    }

    private static void startMonitoring() {
        int interval = 5;
        Observable.interval(interval, TimeUnit.SECONDS).doOnNext(l -> {

            long totalRequestsInLastWindow = getRollingSum(CounterEvent.REQUESTS);

            if (totalRequestsInLastWindow <= 0) {
                return; // Don't print anything if there weren't any requests coming.
            }

            StringBuilder msg = new StringBuilder();
            msg.append("########################################################################################").append(
                    '\n');
            msg.append("Time since start (seconds): " + l * interval).append('\n');
            msg.append("########################################################################################").append(
                    '\n');

            msg.append("Total => ");
            msg.append("  Requests: ").append(counter.getCumulativeSum(CounterEvent.REQUESTS));
            msg.append("  Success: ").append(counter.getCumulativeSum(CounterEvent.SUCCESS));
            msg.append("  Error: ").append(counter.getCumulativeSum(CounterEvent.HTTP_ERROR));
            msg.append("  Netty Error: ").append(counter.getCumulativeSum(CounterEvent.NETTY_ERROR));
            msg.append("  Client Pool Exhausted: ").append(counter.getCumulativeSum(CounterEvent.CLIENT_POOL_EXHAUSTION));
            msg.append("  Socket Exception: ").append(counter.getCumulativeSum(CounterEvent.SOCKET_EXCEPTION));
            msg.append("  I/O Exception: ").append(counter.getCumulativeSum(CounterEvent.IO_EXCEPTION));
            msg.append("  Cancellation Exception: ").append(counter.getCumulativeSum(CounterEvent.CANCELLATION_EXCEPTION));
            msg.append("  Parsing Exception: ").append(counter.getCumulativeSum(CounterEvent.PARSING_EXCEPTION));
            msg.append("  Bytes: ").append(counter.getCumulativeSum(CounterEvent.BYTES) / 1024).append("kb");
            msg.append(" \n   Rolling =>");
            msg.append("  Requests: ").append(getRollingSum(CounterEvent.REQUESTS)).append("/s");
            msg.append("  Success: ").append(getRollingSum(CounterEvent.SUCCESS)).append("/s");
            msg.append("  Error: ").append(getRollingSum(CounterEvent.HTTP_ERROR)).append("/s");
            msg.append("  Netty Error: ").append(getRollingSum(CounterEvent.NETTY_ERROR)).append("/s");
            msg.append("  Bytes: ").append(getRollingSum(CounterEvent.BYTES) / 1024).append("kb/s");
            msg.append(" \n   Latency (ms) => 50th: ").append(latency.getPercentile(50.0)).append(
                    "  90th: ").append(latency.getPercentile(90.0));
            msg.append("  99th: ").append(latency.getPercentile(99.0)).append("  100th: ").append(latency.getPercentile(
                    100.0));
            System.out.println(msg.toString());

            StringBuilder n = new StringBuilder();
            HttpClient<ByteBuf, ByteBuf> httpClient = route.getClient();
            n.append("     Netty => Used: ").append(httpClient.getStats().getInUseCount());
            n.append("  Idle: ").append(httpClient.getStats().getIdleCount());
            n.append("  Total Conns: ").append(httpClient.getStats().getTotalConnectionCount());
            n.append("  AcqReq: ").append(httpClient.getStats().getPendingAcquireRequestCount());
            n.append("  RelReq: ").append(httpClient.getStats().getPendingReleaseRequestCount());
            System.out.println(n.toString());
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
