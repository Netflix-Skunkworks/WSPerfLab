package perf.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
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
        cm.setMaxTotal(10000);
        cm.setDefaultMaxPerRoute(10000);
        httpclient = new DefaultHttpClient(cm);

        // used for parallel execution
        executor = new ThreadPoolExecutor(200, 10000, 1, TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
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

                /* First 2 requests (A, B) in parallel */
                final Future<String> aResponse = queueGet("/mock.json?numItems=2&itemSize=50&delay=50&id=" + id);
                final Future<String> bResponse = queueGet("/mock.json?numItems=25&itemSize=30&delay=150&id=" + id);

                /* When response A received perform C & D */
                // spawned in another thread so we don't block the ability to B/E to proceed in parallel
                Future<BackendResponse[]> aGroupResponses = executor.submit(new Callable<BackendResponse[]>() {

                    @Override
                    public BackendResponse[] call() throws Exception {
                        String aValue = aResponse.get();
                        BackendResponse aResponse = BackendResponse.fromJson(jsonFactory, aValue);
                        final Future<String> cResponse = queueGet("/mock.json?numItems=1&itemSize=5000&delay=80&id=" + aResponse.getResponseKey());
                        final Future<String> dResponse = queueGet("/mock.json?numItems=1&itemSize=1000&delay=1&id=" + aResponse.getResponseKey());
                        return new BackendResponse[] { aResponse, BackendResponse.fromJson(jsonFactory, cResponse.get()),
                                BackendResponse.fromJson(jsonFactory, dResponse.get()) };
                    }

                });

                /* When response B is received perform E */
                String bValue = bResponse.get();
                BackendResponse b = BackendResponse.fromJson(jsonFactory, bValue);
                String eValue = get("/mock.json?numItems=100&itemSize=30&delay=40&id=" + b.getResponseKey());

                BackendResponse e = BackendResponse.fromJson(jsonFactory, eValue);

                /*
                 * Parse JSON so we can extract data and combine data into a single response.
                 *
                 * This simulates what real web-services do most of the time.
                 */
                BackendResponse a = aGroupResponses.get()[0];
                BackendResponse c = aGroupResponses.get()[1];
                BackendResponse d = aGroupResponses.get()[2];

                ByteArrayOutputStream bos = ServiceResponseBuilder.buildTestAResponse(jsonFactory, a, b, c, d, e);
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

    public Future<String> queueGet(final String url) {
        return executor.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                return get(url);
            }

        });
    }

    public String get(String url) {
        String uri = URLSelector.chooseHost() + url;
        HttpGet httpGet = new HttpGet(uri);
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

            return data;
        } catch (Exception e) {
            throw new RuntimeException("Failure retrieving: " + uri, e);
        } finally {
            httpGet.releaseConnection();
        }
    }
}
