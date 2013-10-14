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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonFactory;

import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;
import perf.test.utils.URLSelector;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subscriptions.Subscriptions;
import rx.util.functions.Action1;
import rx.util.functions.Func1;
import rx.util.functions.Func2;

/**
 * Servlet implementation class TestServlet
 */
public class TestCaseAServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final static JsonFactory jsonFactory = new JsonFactory();

    // used for multi-threaded Http requests
    private final PoolingClientConnectionManager cm;
    private final HttpClient httpclient;
    // used for parallel execution of requests
    private final ThreadPoolExecutor executor;

    public TestCaseAServlet() {

        cm = new PoolingClientConnectionManager();
        // set the limit high so this isn't throttling us while we push to the limit
        cm.setMaxTotal(1000);
        cm.setDefaultMaxPerRoute(1000);
        httpclient = new DefaultHttpClient(cm);

        // used for parallel execution
        executor = new ThreadPoolExecutor(200, 1000, 1, TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
    }

    protected void doGet(HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
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
                /**
                 * The following is very obnoxious with all of the types and anonymous inner classes using Java.
                 * This is FAR easier with Groovy/Clojure/whatever
                 */

                /* Fetch A then once it returns fetch C & D */
                Observable<BackendResponse[]> acdValues = get("/mock.json?numItems=2&itemSize=50&delay=50&id=" + id)
                        .mapMany(new Func1<BackendResponse, Observable<BackendResponse[]>>() {

                            @Override
                            public Observable<BackendResponse[]> call(final BackendResponse aValue) {
                                /* When response A received perform C & D */
                                Observable<BackendResponse> cResponse = get("/mock.json?numItems=1&itemSize=5000&delay=80&id=" + aValue.getResponseKey());
                                Observable<BackendResponse> dResponse = get("/mock.json?numItems=1&itemSize=1000&delay=1&id=" + aValue.getResponseKey());
                                return Observable.zip(cResponse, dResponse, new Func2<BackendResponse, BackendResponse, BackendResponse[]>() {

                                    @Override
                                    public BackendResponse[] call(BackendResponse c, BackendResponse d) {
                                        return new BackendResponse[] { aValue, c, d };
                                    }
                                });
                            }
                        });

                /* Fetch B then once it returns fetch E */
                Observable<BackendResponse[]> beValues = get("/mock.json?numItems=25&itemSize=30&delay=150&id=" + id)
                        .mapMany(new Func1<BackendResponse, Observable<BackendResponse[]>>() {

                            @Override
                            public Observable<BackendResponse[]> call(final BackendResponse b) {
                                Observable<BackendResponse> eResponse = get("/mock.json?numItems=100&itemSize=30&delay=40&id=" + b.getResponseKey());
                                return eResponse.map(new Func1<BackendResponse, BackendResponse[]>() {

                                    @Override
                                    public BackendResponse[] call(BackendResponse e) {
                                        return new BackendResponse[] { b, e };
                                    }
                                });
                            }

                        });

                Observable.zip(acdValues, beValues, new Func2<BackendResponse[], BackendResponse[], BackendResponse[]>() {

                    @Override
                    public BackendResponse[] call(BackendResponse[] acd, BackendResponse[] be) {
                        // array of a, b, c, d, e
                        return new BackendResponse[] { acd[0], be[0], acd[1], acd[2], be[1] };
                    }

                }).forEach(new Action1<BackendResponse[]>() {

                    @Override
                    public void call(BackendResponse[] be) {
                        try {
                            ByteArrayOutputStream bos = ServiceResponseBuilder.buildTestAResponse(jsonFactory, be[0], be[1], be[2], be[3], be[4]);
                            // output to stream
                            response.getWriter().write(bos.toString());
                        } catch (Exception e) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    }
                });

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

    public Observable<BackendResponse> get(final String url) {
        return Observable.create(new Func1<Observer<BackendResponse>, Subscription>() {

            @Override
            public Subscription call(final Observer<BackendResponse> observer) {
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        HttpGet httpGet = new HttpGet(URLSelector.chooseHost() + url);
                        try {
                            HttpResponse response = httpclient.execute(httpGet);

                            if (response.getStatusLine().getStatusCode() != 200) {
                                throw new RuntimeException("Failure: " + response.getStatusLine());
                            }

                            HttpEntity entity = response.getEntity();

                            // get response data
                            String data = EntityUtils.toString(entity);

                            // ensure it is fully consumed
                            EntityUtils.consume(entity);

                            observer.onNext(BackendResponse.fromJson(jsonFactory, data));
                            observer.onCompleted();
                        } catch (Exception e) {
                            observer.onError(new RuntimeException("Failure retrieving: " + url, e));
                        } finally {
                            httpGet.releaseConnection();
                        }
                    }

                });
                /*
                 * We're doing only single values inside the above work so unsubscribe
                 * isn't needed to shortcut the work so we'll return an empty subscription.
                 */
                return Subscriptions.empty();

            }
        });
    }

}
