package perf.test.rxnetty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientBuilder;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.JsonFactory;

import perf.test.utils.BackendResponse;
import perf.test.utils.JsonParseException;
import perf.test.utils.ServiceResponseBuilder;
import rx.Observable;

/**
 * Handle request and return a response that composes multiple backend services like this:
 * 
 * A) GET http://hostname:9100/mock.json?numItems=2&itemSize=50&delay=50&id={uuid}
 * B) GET http://hostname:9100/mock.json?numItems=25&itemSize=30&delay=150&id={uuid}
 * C) GET http://hostname:9100/mock.json?numItems=1&itemSize=5000&delay=80&id={a.responseKey}
 * D) GET http://hostname:9100/mock.json?numItems=1&itemSize=1000&delay=1&id={a.responseKey}
 * E) GET http://hostname:9100/mock.json?numItems=100&itemSize=30&delay=4&id={b.responseKey}
 */
public class TestRouteBasic {

    private static JsonFactory jsonFactory = new JsonFactory();

    private final String host;
    private final int port;

    private final HttpClient<ByteBuf, ByteBuf> client;
    private ConnectionPoolMetricListener stats;

    public TestRouteBasic(String backendHost, int backendPort) {
        host = backendHost;
        port = backendPort;
        client = new HttpClientBuilder<ByteBuf, ByteBuf>(host, port)
                .withMaxConnections(10000)
                .config(new HttpClient.HttpClientConfig.Builder().readTimeout(1, TimeUnit.MINUTES).build())
                .build();
        stats = new ConnectionPoolMetricListener();
        client.subscribe(stats);
    }

    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        long startTime = System.currentTimeMillis();
        List<String> _id = request.getQueryParameters().get("id");
        if (_id == null || _id.size() != 1) {
            return writeError(request, response, "Please provide a numerical 'id' value. It can be a random number (uuid).");
        }
        long id = Long.parseLong(String.valueOf(_id.get(0)));

        Observable<List<BackendResponse>> acd = getDataFromBackend("/mock.json?numItems=2&itemSize=50&delay=50&id=" + id)
                .doOnError(Throwable::printStackTrace)
                // Eclipse 20140224-0627 can't infer without this type hint even though the Java 8 compiler can
                .<List<BackendResponse>> flatMap(responseA -> {
                    Observable<BackendResponse> responseC = getDataFromBackend(
                            "/mock.json?numItems=1&itemSize=5000&delay=80&id=" + responseA.getResponseKey());
                    Observable<BackendResponse> responseD = getDataFromBackend(
                            "/mock.json?numItems=1&itemSize=1000&delay=1&id=" + responseA.getResponseKey());
                    return Observable.zip(Observable.just(responseA), responseC, responseD,
                                          Arrays::asList);
                }).doOnError(Throwable::printStackTrace);

        Observable<List<BackendResponse>> be = getDataFromBackend("/mock.json?numItems=25&itemSize=30&delay=150&id=" + id)
                // Eclipse 20140224-0627 can't infer without this type hint even though the Java 8 compiler can
                .<List<BackendResponse>> flatMap(responseB -> {
                    Observable<BackendResponse> responseE = getDataFromBackend(
                            "/mock.json?numItems=100&itemSize=30&delay=4&id=" + responseB.getResponseKey());
                    return Observable.zip(Observable.just(responseB), responseE, Arrays::asList);
                }).doOnError(Throwable::printStackTrace);

        return Observable.zip(acd, be, (_acd, _be) -> {
            BackendResponse responseA = _acd.get(0);
            BackendResponse responseB = _be.get(0);
            BackendResponse responseC = _acd.get(1);
            BackendResponse responseD = _acd.get(2);
            BackendResponse responseE = _be.get(1);

            return new BackendResponse[] { responseA, responseB, responseC, responseD, responseE };
        }).flatMap(backendResponses -> {
            try {
                ByteArrayOutputStream responseStream = ServiceResponseBuilder.buildTestAResponse(jsonFactory, backendResponses);
                // set response header
                response.getHeaders().addHeader("Content-Type", "application/json");
                // performance headers
                addResponseHeaders(response, startTime);
                int contentLength = responseStream.size();
                response.getHeaders().addHeader("Content-Length", contentLength);
                return response.writeBytesAndFlush(responseStream.toByteArray());
            } catch (Exception e) {
                return writeError(request, response, "Failed: " + e.getMessage());
            }
        }).doOnError(Throwable::printStackTrace);
    }

    public HttpClient<ByteBuf, ByteBuf> getClient() {
        return client;
    }

    /**
     * Add various headers used for logging and statistics.
     */
    private static void addResponseHeaders(HttpServerResponse<?> response, long startTime) {
        Map<String, String> perfResponseHeaders = ServiceResponseBuilder.getPerfResponseHeaders(startTime);
        for (Map.Entry<String, String> entry : perfResponseHeaders.entrySet()) {
            response.getHeaders().add(entry.getKey(), entry.getValue());
        }
    }

    private Observable<BackendResponse> getDataFromBackend(String url) {
        return client.submit(HttpClientRequest.createGet(url)).flatMap((HttpClientResponse<ByteBuf> r) -> {
            Observable<BackendResponse> bytesToJson = r.getContent().flatMap(b -> {
                try {
                    return Observable.just(BackendResponse.fromJson(jsonFactory, new ByteBufInputStream(b)));
                } catch (JsonParseException e) {
                    return Observable.error(e);
                }
            });
            return bytesToJson;
        });
    }

    private static Observable<Void> writeError(HttpServerRequest<?> request, HttpServerResponse<?> response, String message) {
        System.err.println("Server => Error [" + request.getPath() + "] => " + message);
        response.setStatus(HttpResponseStatus.BAD_REQUEST);
        return response.writeStringAndFlush("Error 500: " + message + "\n");
    }

    public ConnectionPoolMetricListener getStats() {
        return stats;
    }
}
