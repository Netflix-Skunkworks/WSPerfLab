package perf.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.codehaus.jackson.JsonFactory;

import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;
import rx.Observable;
import rx.apache.http.ObservableHttp;
import rx.apache.http.ObservableHttpResponse;
import rx.util.functions.Func1;
import rx.util.functions.Func2;
import rx.util.functions.Func3;

/**
 * Servlet implementation class TestServlet
 */
public class TestCaseAServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final static JsonFactory jsonFactory = new JsonFactory();

    final CloseableHttpAsyncClient httpClient;
    // used for parallel execution of requests
    private final ThreadPoolExecutor executor;

    private final String hostname;

    public TestCaseAServlet() {

        // hostname via properties
        String host = System.getProperty("perf.test.backend.hostname");
        if (host == null) {
            throw new IllegalStateException("The perf.test.backend.hostname property must be set.");
        }
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        hostname = host;

        final RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(500).build();
        this.httpClient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                // set the limit high so this isn't throttling us while we push to the limit
                .setMaxConnPerRoute(1000)
                .setMaxConnTotal(1000)
                .build();

        // used for parallel execution
        executor = new ThreadPoolExecutor(200, 1000, 1, TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            Object _id = request.getParameter("id");
            if (_id == null) {
                response.getWriter().println("Please provide a numerical 'id' value. It can be a random number (uuid).");
                response.setStatus(500);
                return;
            }
            final long id = Long.parseLong(String.valueOf(_id));

            try {

                /* A, C, D call graph */
                final Observable<BackendResponse[]> acdResponse = get("/mock.json?numItems=2&itemSize=50&delay=50&id=" + id)
                        .flatMap(new Func1<String, Observable<BackendResponse[]>>() {
                            /* When response A received perform C & D */

                            @Override
                            public Observable<BackendResponse[]> call(final String aValue) {
                                return BackendResponse.fromJsonToObservable(jsonFactory, aValue).flatMap(new Func1<BackendResponse, Observable<BackendResponse[]>>() {

                                    @Override
                                    public Observable<BackendResponse[]> call(BackendResponse aResponse) {

                                        Observable<BackendResponse> cResponse = get("/mock.json?numItems=1&itemSize=5000&delay=80&id=" + aResponse.getResponseKey())
                                                .flatMap(new Func1<String, Observable<BackendResponse>>() {

                                                    @Override
                                                    public Observable<BackendResponse> call(String cValue) {
                                                        return BackendResponse.fromJsonToObservable(jsonFactory, cValue);
                                                    }
                                                });
                                        Observable<BackendResponse> dResponse = get("/mock.json?numItems=1&itemSize=1000&delay=1&id=" + aResponse.getResponseKey())
                                                .flatMap(new Func1<String, Observable<BackendResponse>>() {

                                                    @Override
                                                    public Observable<BackendResponse> call(String dValue) {
                                                        return BackendResponse.fromJsonToObservable(jsonFactory, dValue);
                                                    }
                                                });

                                        return Observable.zip(Observable.just(aResponse), cResponse, dResponse, new Func3<BackendResponse, BackendResponse, BackendResponse, BackendResponse[]>() {

                                            @Override
                                            public BackendResponse[] call(BackendResponse aResponse, BackendResponse cResponse, BackendResponse dResponse) {
                                                return new BackendResponse[] { aResponse, cResponse, dResponse };
                                            }

                                        });

                                    }

                                });

                            }

                        });

                /* B, E call graph */
                final Observable<BackendResponse[]> beResponse = get("/mock.json?numItems=25&itemSize=30&delay=150&id=" + id)
                        .flatMap(new Func1<String, Observable<BackendResponse[]>>() {
                            /* When response B received perform E */

                            @Override
                            public Observable<BackendResponse[]> call(final String bValue) {
                                return BackendResponse.fromJsonToObservable(jsonFactory, bValue).flatMap(new Func1<BackendResponse, Observable<BackendResponse[]>>() {

                                    @Override
                                    public Observable<BackendResponse[]> call(BackendResponse bResponse) {
                                        Observable<BackendResponse> eResponse = get("/mock.json?numItems=100&itemSize=30&delay=40&id=" + bResponse.getResponseKey())
                                                .flatMap(new Func1<String, Observable<BackendResponse>>() {

                                                    @Override
                                                    public Observable<BackendResponse> call(String eValue) {
                                                        return BackendResponse.fromJsonToObservable(jsonFactory, eValue);
                                                    }
                                                });

                                        return Observable.zip(Observable.just(bResponse), eResponse, new Func2<BackendResponse, BackendResponse, BackendResponse[]>() {

                                            @Override
                                            public BackendResponse[] call(BackendResponse bResponse, BackendResponse eResponse) {
                                                return new BackendResponse[] { bResponse, eResponse };
                                            }

                                        });
                                    }
                                });

                            }

                        });

                Observable<BackendResponse[]> completeResponse = Observable.zip(acdResponse, beResponse, new Func2<BackendResponse[], BackendResponse[], BackendResponse[]>() {

                    @Override
                    public BackendResponse[] call(BackendResponse[] acd, BackendResponse[] be) {
                        return new BackendResponse[] { acd[0], acd[1], acd[2], be[0], be[1] };
                    };

                });

                // blocking servlet call so block here
                BackendResponse[] r = completeResponse.toBlockingObservable().single();

                ByteArrayOutputStream bos = ServiceResponseBuilder.buildTestAResponse(jsonFactory, r[0], r[1], r[2], r[3], r[4]);
                // output to stream
                response.getWriter().write(bos.toString());
            } catch (Exception e) {
                // error that needs to be returned
                response.setStatus(500);
                response.getWriter().println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            ServiceResponseBuilder.addResponseHeaders(response, startTime);
        }
    }

    public Observable<String> get(String url) {
        String uri = hostname + url;
        return ObservableHttp.createGet("uri", httpClient).toObservable().flatMap(new Func1<ObservableHttpResponse, Observable<String>>() {

            @Override
            public Observable<String> call(ObservableHttpResponse response) {
                if (response.getResponse().getStatusLine().getStatusCode() != 200) {
                    return Observable.error(new RuntimeException("Failure: " + response.getResponse().getStatusLine()));
                } else {
                    return response.getContent().map(new Func1<byte[], String>() {

                        @Override
                        public String call(byte[] bb) {
                            return new String(bb);
                        }

                    });
                }

            }
        });

    }
}
