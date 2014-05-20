package perf.backend;

import com.netflix.numerus.NumerusRollingNumber;
import com.netflix.numerus.NumerusRollingPercentile;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.netflix.numerus.NumerusProperty.Factory.asProperty;

public class StartMockService {

    static final int rollingSeconds = 5;

    static final NumerusRollingNumber counter = new NumerusRollingNumber(CounterEvent.SUCCESS,
                                                                  asProperty( rollingSeconds * 1000), asProperty(10));
    static final NumerusRollingPercentile latency = new NumerusRollingPercentile(asProperty(rollingSeconds * 1000),
                                                                          asProperty(10),
                                                                          asProperty(1000), asProperty(Boolean.TRUE));

    public static void main(String[] args) {
        int port = 8989;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        System.out.println("Starting mock service on port " + port + "...");
        startMonitoring();
        RxNetty.createHttpServer(port, (request, response) -> {
            try {
                long startTime = System.currentTimeMillis();
                counter.increment(CounterEvent.REQUESTS);
                return handleRequest(request, response)
                        .doOnCompleted(() ->  {
                            counter.increment(CounterEvent.SUCCESS);
                            latency.addValue((int)(System.currentTimeMillis() - startTime));
                        })
                        .doOnError(t -> counter.increment(CounterEvent.NETTY_ERROR));
            } catch (Throwable e) {
                System.err.println("Server => Error [" + request.getPath() + "] => " + e);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                counter.increment(CounterEvent.HTTP_ERROR);
                return response.writeStringAndFlush("Error 500: Bad Request\n" + e.getMessage() + '\n');
            }
        }).startAndWait();
    }

    protected static Observable<Void> writeError(HttpServerRequest<?> request, HttpServerResponse<?> response, String message) {
        System.err.println("Server => Error [" + request.getPath() + "] => " + message);
        response.setStatus(HttpResponseStatus.BAD_REQUEST);
        counter.increment(CounterEvent.HTTP_ERROR);
        return response.writeStringAndFlush("Error 500: " + message + '\n');
    }

    protected static int getParameter(HttpServerRequest<?> request, String key, int defaultValue) {
        List<String> v = request.getQueryParameters().get(key);
        if (v == null || v.size() != 1) {
            return defaultValue;
        } else {
            return Integer.parseInt(String.valueOf(v.get(0)));
        }
    }

    private static Observable<Void> handleRequest(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        List<String> _id = request.getQueryParameters().get("id");
        if (_id == null || _id.size() != 1) {
            return writeError(request, response, "Please provide a numerical 'id' value. It can be a random number (uuid). Received => " + _id);
        }
        long id = Long.parseLong(String.valueOf(_id.get(0)));

        int delay = getParameter(request, "delay", 50); // default to 50ms server-side delay
        int itemSize = getParameter(request, "itemSize", 128); // default to 128 bytes item size (assuming ascii text)
        int numItems = getParameter(request, "numItems", 10); // default to 10 items in a list

        // no more than 100 items
        if (numItems < 1 || numItems > 100) {
            return writeError(request, response, "Please choose a 'numItems' value from 1 to 100.");
        }

        // no larger than 50KB per item
        if (itemSize < 1 || itemSize > 1024 * 50) {
            return writeError(request, response, "Please choose an 'itemSize' value from 1 to 1024*50 (50KB).");
        }

        // no larger than 60 second delay
        if (delay < 0 || delay > 60000) {
            return writeError(request, response, "Please choose a 'delay' value from 0 to 60000 (60 seconds).");
        }

        response.setStatus(HttpResponseStatus.OK);
        return MockResponse.generateJson(id, delay, itemSize, numItems)
                           .doOnNext(json -> counter.add(CounterEvent.BYTES, json.readableBytes()))
                           .flatMap(json -> response.writeAndFlush(json));
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
            msg.append(" \n   Rolling =>");
            msg.append("  Success: ").append(getRollingSum(CounterEvent.SUCCESS)).append("/s");
            msg.append("  Error: ").append(getRollingSum(CounterEvent.HTTP_ERROR)).append("/s");
            msg.append("  Netty Error: ").append(getRollingSum(CounterEvent.NETTY_ERROR)).append("/s");
            msg.append("  Bytes: ").append(getRollingSum(CounterEvent.BYTES) / 1024).append("kb/s");
            msg.append(" \n   Latency (ms) => 50th: ").append(latency.getPercentile(50.0)).append(
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
