package perf.test.ribbon;

import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.proxy.RibbonDynamicProxy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.codehaus.jackson.JsonFactory;
import perf.test.utils.BackendResponse;
import perf.test.utils.JsonParseException;
import perf.test.utils.ServiceResponseBuilder;
import rx.Observable;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class TestRouteBasic {

    private final ConnectionPoolMetricListener metricListener;

    private static JsonFactory jsonFactory = new JsonFactory();

    private final HttpResourceGroup httpResourceGroup;
    private final MockBackendService service;

    public TestRouteBasic(String backendServerList) {
        httpResourceGroup = new HttpResourceGroup("performanceTest", ClientOptions.create()
                .withMaxAutoRetries(0)
                .withMaxAutoRetriesNextServer(0)
                .withConnectTimeout(30000)
                .withReadTimeout(30000)
                .withMaxConnectionsPerHost(10000)
                .withMaxTotalConnections(10000)
                .withConfigurationBasedServerList(backendServerList));

        metricListener = new ConnectionPoolMetricListener();
        httpResourceGroup.getClient().subscribe(metricListener);

        service = RibbonDynamicProxy.newInstance(MockBackendService.class, httpResourceGroup);
    }

    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        long startTime = System.currentTimeMillis();
        List<String> _id = request.getQueryParameters().get("id");

        if (_id == null || _id.size() != 1) {
            return writeError(request, response, "Please provide a numerical 'id' value. It can be a random number (uuid).");
        }
        long id = Long.parseLong(String.valueOf(_id.get(0)));


        Observable<List<BackendResponse>> acd = getDataFromBackend(2, 50, 50, id)
                .doOnError(Throwable::printStackTrace)
                        // Eclipse 20140224-0627 can't infer without this type hint even though the Java 8 compiler can
                .<List<BackendResponse>>flatMap(responseA -> {
                    Observable<BackendResponse> responseC = getDataFromBackend(1, 5000, 80, responseA.getResponseKey());
                    Observable<BackendResponse> responseD = getDataFromBackend(1, 1000, 1, responseA.getResponseKey());
                    return Observable.zip(Observable.just(responseA), responseC, responseD,
                            Arrays::asList);
                }).doOnError(Throwable::printStackTrace);

        Observable<List<BackendResponse>> be = getDataFromBackend(25, 30, 150, id)
                // Eclipse 20140224-0627 can't infer without this type hint even though the Java 8 compiler can
                .<List<BackendResponse>>flatMap(responseB -> {
                    Observable<BackendResponse> responseE = getDataFromBackend(100, 30, 4, responseB.getResponseKey());
                    return Observable.zip(Observable.just(responseB), responseE, Arrays::asList);
                }).doOnError(Throwable::printStackTrace);

        return Observable.zip(acd, be, (_acd, _be) -> {
            BackendResponse responseA = _acd.get(0);
            BackendResponse responseB = _be.get(0);
            BackendResponse responseC = _acd.get(1);
            BackendResponse responseD = _acd.get(2);
            BackendResponse responseE = _be.get(1);

            BackendResponse[] backendResponses = {responseA, responseB, responseC, responseD, responseE};
            checkForHystrixCallbacks(backendResponses);
            return backendResponses;
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
                e.printStackTrace();
                return writeError(request, response, "Failed: " + e.getMessage());
            }
        }).doOnError(Throwable::printStackTrace);
    }

    public ConnectionPoolMetricListener getStats() {
        return metricListener;
    }

    private void checkForHystrixCallbacks(BackendResponse[] backendResponses) {
        for (BackendResponse r : backendResponses) {
            if (r.isFallback()) {
                StartServer.counter.increment(CounterEvent.HYSTRIX_FALLBACK);
                return;
            }
        }
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

    private Observable<BackendResponse> getDataFromBackend(Integer numItems, Integer itemSize, Integer delay, Long id) {
        return service.request(numItems, itemSize, delay, id).toObservable().flatMap(b -> {
            try {
                return Observable.just(BackendResponse.fromJson(jsonFactory, new ByteBufInputStream(b)));
            } catch (JsonParseException e) {
                return Observable.error(e);
            }
        });
    }

    private static Observable<Void> writeError(HttpServerRequest<?> request, HttpServerResponse<?> response, String message) {
        System.err.println("Server => Error [" + request.getPath() + "] => " + message);
        response.setStatus(HttpResponseStatus.BAD_REQUEST);
        return response.writeStringAndFlush("Error 500: " + message + "\n");
    }
}
