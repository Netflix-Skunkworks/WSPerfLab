package perf.test;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonFactory;
import perf.test.utils.BackendMockHostSelector;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Servlet implementation class TestServlet
 */
public class TestCaseAServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final static JsonFactory jsonFactory = new JsonFactory();

    private final HttpClient client;

    // used for parallel execution of requests
    private final ThreadPoolExecutor executor;

    private static final class RequestIdHolder {

        private static final String NO_REQUEST_ID = "[no-request-id]";

        private static final InheritableThreadLocal<String> REQUEST_ID = new InheritableThreadLocal<String>() {
            @Override
            protected String initialValue() {
                return NO_REQUEST_ID;
            }
        };

        String get() {
            final String requestId = REQUEST_ID.get();
            return requestId;
        }

        String init() {
            final String reqId = UUID.randomUUID().toString();
            REQUEST_ID.set(reqId);
            return reqId;
        }

        void clear() {
            REQUEST_ID.set(NO_REQUEST_ID);
        }

    }

    private static final RequestIdHolder requestIdHolder = new RequestIdHolder();

    public TestCaseAServlet() {
        final RequestConfig reqConfig = RequestConfig.custom()
            .setConnectTimeout(PropertyNames.ClientConnectTimeout.getValueAsInt())
            .setSocketTimeout(PropertyNames.ClientSocketTimeout.getValueAsInt())
            .setConnectionRequestTimeout(PropertyNames.ClientConnectionRequestTimeout.getValueAsInt())
            .build();

        // don't care about total vs. per-route right now, will set them to the same
        final PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
        connMgr.setMaxTotal(PropertyNames.ClientMaxConnectionsTotal.getValueAsInt());
        connMgr.setDefaultMaxPerRoute(PropertyNames.ClientMaxConnectionsTotal.getValueAsInt());

        this.client = HttpClients.custom()
            .setDefaultRequestConfig(reqConfig)
            .setConnectionManager(connMgr)
            .build();

        // used for parallel execution
        final int backendRequestThreadPoolSize = PropertyNames.BackendRequestThreadPoolSize.getValueAsInt();

        // setting core and max pool sizes the same since I do not want any queueing in here
        executor = new ThreadPoolExecutor(backendRequestThreadPoolSize,
            backendRequestThreadPoolSize,
            5,
            TimeUnit.MINUTES,
            new LinkedBlockingQueue<Runnable>());
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        final String requestId = requestIdHolder.init();
        ////EventLogger.log(requestId, "request-start");
/*
        final PerformanceLogger perfLogger = PerformanceLogger.instance();
        perfLogger.start(requestId, "top");
*/

        try {

            Object _id = request.getParameter("id");
            if (_id == null) {
                response.getWriter().println("Please provide a numerical 'id' value. It can be a random number (uuid).");
                response.setStatus(500);
                return;
            }
            final long id = Long.parseLong(String.valueOf(_id));

            try {

                /* First 2 requests (A, B) in parallel */
                final Future<String> aResponse = queueGet(requestId, "/mock.json?type=A&numItems=2&itemSize=50&delay=50&id=" + id);
                final Future<String> bResponse = queueGet(requestId, "/mock.json?type=B&numItems=25&itemSize=30&delay=150&id=" + id);

                /* When response A received perform C & D */
                // spawned in another thread so we don't block the ability to B/E to proceed in parallel
                Future<BackendResponse[]> aGroupResponses = executor.submit(new Callable<BackendResponse[]>() {

                    @Override
                    public BackendResponse[] call() throws Exception {
                        String aValue = aResponse.get();
                        BackendResponse aResponse = BackendResponse.fromJson(jsonFactory, aValue);
                        final Future<String> cResponse = queueGet(requestId, "/mock.json?type=C&numItems=1&itemSize=5000&delay=80&id=" + aResponse.getResponseKey());
                        final Future<String> dResponse = queueGet(requestId, "/mock.json?type=D&numItems=1&itemSize=1000&delay=1&id=" + aResponse.getResponseKey());
                        return new BackendResponse[] { aResponse, BackendResponse.fromJson(jsonFactory, cResponse.get()),
                                BackendResponse.fromJson(jsonFactory, dResponse.get()) };
                    }

                });

                /* When response B is received perform E */
                String bValue = bResponse.get();
                BackendResponse b = BackendResponse.fromJson(jsonFactory, bValue);
                String eValue = get(requestId, "/mock.json?type=E&numItems=100&itemSize=30&delay=40&id=" + b.getResponseKey());

                BackendResponse e = BackendResponse.fromJson(jsonFactory, eValue);

                /*
                 * Parse JSON so we can extract data and combine data into a single response.
                 * 
                 * This simulates what real web-services do most of the time.
                 */
                BackendResponse a = aGroupResponses.get()[0];
                BackendResponse c = aGroupResponses.get()[1];
                BackendResponse d = aGroupResponses.get()[2];

                //EventLogger.log(requestId, "build-response-start");
                ByteArrayOutputStream bos = ServiceResponseBuilder.buildTestAResponse(jsonFactory, a, b, c, d, e);
                //EventLogger.log(requestId, "build-response-end");

                // output to stream
                //EventLogger.log(requestId, "flush-response-start");
                response.getWriter().write(bos.toString());
                //EventLogger.log(requestId, "flush-response-end");
            } catch (Exception e) {
                // error that needs to be returned
                response.setStatus(500);
                response.getWriter().println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            ServiceResponseBuilder.addResponseHeaders(response, startTime);
            //perfLogger.stop(requestId, "top");
            //EventLogger.log(requestId, "request-end");
            requestIdHolder.clear();
        }
    }

    public Future<String> queueGet(final String requestId, final String url) {
        final Future<String> f = executor.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                return get(requestId, url);
            }

        });

        //EventLogger.log(requestId, "backend-request-submit " + url);
        return f;
    }

    public String get(String requestId, String url) {
        String uri = BackendMockHostSelector.getRandomBackendPathPrefix() + url;

        //final PerformanceLogger perfLogger = PerformanceLogger.instance();
        final String perfKey = "backend-request " + uri;
        //perfLogger.start(requestId, perfKey);
        //EventLogger.log(requestId, "backend-request-start " + uri);

        HttpGet httpGet = new HttpGet(uri);
        try {
            HttpResponse response = this.client.execute(httpGet);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failure: " + response.getStatusLine());
            }

            HttpEntity entity = response.getEntity();

            // get response data
            String data = EntityUtils.toString(entity);

            // ensure it is fully consumed
            EntityUtils.consume(entity);

            return data;
        } catch (Exception e) {
            throw new RuntimeException("Failure retrieving: " + uri, e);
        } finally {
            httpGet.releaseConnection();
            //perfLogger.stop(requestId, perfKey);
            //EventLogger.log(requestId, "backend-request-end " + uri);
        }
    }
}
