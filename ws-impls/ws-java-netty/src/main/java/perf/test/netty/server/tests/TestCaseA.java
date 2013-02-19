package perf.test.netty.server.tests;

import com.google.common.base.Throwables;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpResponse;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponeBuilder;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class TestCaseA extends TestCaseHandler {

    private final ThreadPoolExecutor executor;

    public TestCaseA() throws InterruptedException {
        super("testA");
        executor = new ThreadPoolExecutor(200, 1000, 1, TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
    }

    @Override
    protected void executeTestCase(String id, HttpResponse response) throws Throwable {

        /* First 2 requests (A, B) in parallel */
        final Future<String> aResponse = get("/mock.json?numItems=2&itemSize=50&delay=50&id=" + id);
        final Future<String> bResponse = get("/mock.json?numItems=25&itemSize=30&delay=150&id=" + id);
        final int timeout = 300;
        /* When response A received perform C & D */
        // spawned in another thread so we don't block the ability to B/E to proceed in parallel
        Future<BackendResponse[]> aGroupResponses = executor.submit(new Callable<BackendResponse[]>() {

            @Override
            public BackendResponse[] call() throws Exception {
                String aValue = aResponse.get(timeout, TimeUnit.MILLISECONDS);
                BackendResponse aResponse = BackendResponse.fromJson(jsonFactory, aValue);
                final Future<String> cResponse;
                try {
                    cResponse = get(
                            "/mock.json?numItems=1&itemSize=5000&delay=80&id=" + aResponse.getResponseKey());
                    final Future<String> dResponse = get(
                            "/mock.json?numItems=1&itemSize=1000&delay=1&id=" + aResponse.getResponseKey());
                    return new BackendResponse[]{aResponse, BackendResponse
                            .fromJson(jsonFactory, cResponse.get(timeout, TimeUnit.MILLISECONDS)), BackendResponse
                            .fromJson(jsonFactory, dResponse.get(timeout, TimeUnit.MILLISECONDS))};
                } catch (Throwable throwable) {
                    throw Throwables.propagate(throwable);
                }
            }

        });

        /* When response B is received perform E */
        String bValue = bResponse.get(timeout, TimeUnit.MILLISECONDS);
        BackendResponse b = BackendResponse.fromJson(jsonFactory, bValue);
        Future<String> eValue = get("/mock.json?numItems=100&itemSize=30&delay=40&id=" + b.getResponseKey());

        BackendResponse e = BackendResponse.fromJson(jsonFactory, eValue.get(timeout, TimeUnit.MILLISECONDS));

        /*
    * Parse JSON so we can extract data and combine data into a single response.
    *
    * This simulates what real web-services do most of the time.
    */
        BackendResponse a = aGroupResponses.get(timeout, TimeUnit.MILLISECONDS)[0];
        BackendResponse c = aGroupResponses.get(timeout, TimeUnit.MILLISECONDS)[1];
        BackendResponse d = aGroupResponses.get(timeout, TimeUnit.MILLISECONDS)[2];

        ByteArrayOutputStream outputStream = ServiceResponeBuilder.buildTestAResponse(jsonFactory, a, b, c, d, e);

        // output to stream
        response.setContent(ChannelBuffers.copiedBuffer(outputStream.toByteArray()));
    }

}
