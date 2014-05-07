package perf.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.codehaus.jackson.JsonFactory;

import perf.test.utils.BackendMockHostSelector;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.apache.http.ObservableHttp;
import rx.apache.http.ObservableHttpResponse;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;

/**
 * Servlet implementation class TestServlet
 */
public class TestCaseAServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final static JsonFactory jsonFactory = new JsonFactory();

    final CloseableHttpAsyncClient httpClient;

    public TestCaseAServlet() {
        final RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(500).build();
        this.httpClient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                // set the limit high so this isn't throttling us while we push to the limit
                .setMaxConnPerRoute(5000)
                .setMaxConnTotal(5000)
                .build();
        this.httpClient.start();
    }

    protected void doGet(HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final long startTime = System.currentTimeMillis();
        try {
            Object _id = request.getParameter("id");
            if (_id == null) {
                response.getWriter().println("Please provide a numerical 'id' value. It can be a random number (uuid).");
                response.setStatus(500);
                return;
            }

            long id = Long.parseLong(String.valueOf(_id));

            Observable<ByteArrayOutputStream> work = defineWork(id);

            final AsyncContext async = request.startAsync();
            Observable<ServletOutputStream> writerAvailable = Observable.create(new AsyncWriterObservable(async, response.getOutputStream()));

            // when work and writer are available (concurrently subscribed to in zip) we then write to the output stream
            Observable.merge(Observable.zip(work, writerAvailable, new Func2<ByteArrayOutputStream, ServletOutputStream, Observable<Void>>() {

                @Override
                public Observable<Void> call(ByteArrayOutputStream workOut, ServletOutputStream servletOut) {
                    try {
                        // this has to be before writing the output
                        ServiceResponseBuilder.addResponseHeaders(response, startTime);
                        // Jetty 9.1 supports using ByteBuf instead of copying between arrays like this
                        // so that may be worth exploring
                        servletOut.write(workOut.toByteArray());
                        servletOut.flush();
                        return Observable.empty();
                    } catch (IOException e) {
                        return Observable.error(e);
                    }
                }

            })).subscribe(new Observer<Void>() {

                @Override
                public void onCompleted() {
                    async.complete();
                }

                @Override
                public void onError(Throwable e) {
                    e.printStackTrace();
                    // error that needs to be returned
                    response.setStatus(500);
                    try {
                        response.getWriter().println("Error: " + e.getMessage());
                    } catch (IOException e1) {
                        // if this also throws an error we have to blow up badly
                        e.printStackTrace();
                        throw new RuntimeException("OnError failed to output error to writer.", e);
                    } finally {
                        async.complete();
                    }

                }

                @Override
                public void onNext(Void args) {
                    // success
                }
            });

        } catch (Exception e) {
            // error that needs to be returned
            response.setStatus(500);
            response.getWriter().println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Observable<String> get(String url) {
        String uri = BackendMockHostSelector.getRandomBackendPathPrefix() + url;
        return ObservableHttp.createGet(uri, httpClient).toObservable().flatMap(new Func1<ObservableHttpResponse, Observable<String>>() {

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

    public Observable<ByteArrayOutputStream> defineWork(long id) {
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
                return new BackendResponse[] { acd[0], be[0], acd[1], acd[2], be[1] };
            };

        });

        return completeResponse.flatMap(new Func1<BackendResponse[], Observable<ByteArrayOutputStream>>() {

            @Override
            public Observable<ByteArrayOutputStream> call(BackendResponse[] r) {
                try {
                    return Observable.just(ServiceResponseBuilder.buildTestAResponse(jsonFactory, r[0], r[1], r[2], r[3], r[4]));
                } catch (IOException e) {
                    return Observable.error(e);
                }
            }

        });
    }

    private final class AsyncWriterObservable implements OnSubscribe<ServletOutputStream> {
        private final AsyncContext async;
        private final ServletOutputStream out;

        private AsyncWriterObservable(AsyncContext async, ServletOutputStream out) {
            this.async = async;
            this.out = out;
        }

        @Override
        public void call(final Subscriber<? super ServletOutputStream> s) {
            out.setWriteListener(new WriteListener() {

                public void onWritePossible() throws IOException {
                    if (!s.isUnsubscribed()) {
                        if (out.isReady()) {
                            s.onNext(out);
                            // TODO fix hack to work around zip bug
                            s.onCompleted();
                        }
                    }
                }

                public void onError(Throwable t) {
                    getServletContext().log("Async Error", t);
                    async.complete();
                    s.onError(t);
                }

            });
        }
    }
}
