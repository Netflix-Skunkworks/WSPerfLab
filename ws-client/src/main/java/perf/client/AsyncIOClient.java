package perf.client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpFields;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant
 */
public class AsyncIOClient {

    private HttpClient httpClient = new HttpClient();
    private ExecutorService loaderThreadPool = Executors.newCachedThreadPool(new ThreadFactory() {

        private final AtomicInteger threadCount = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "WsClientLoader-" + threadCount.incrementAndGet());
        }
    });

    private boolean collectIndividualResults = true;
    private int concurrentClients;
    private String testUri;
    private final Random idGenerator = new Random();
    private final ResultStatsCollector resultStatsCollector = new ResultStatsCollector();
    private final AtomicLong requestCount = new AtomicLong();
    private CountDownLatch finishingLatch;
    private long totalRequests;
    private long requestTimeoutMs = Integer.MAX_VALUE;
    private volatile boolean stopped;
    private TestResult result = new TestResult();
    private final StatusUpdater statusUpdater = new StatusUpdater(80);

    public void start(Runnable onCompleteHandler) throws Exception {
        finishingLatch = new CountDownLatch(concurrentClients);
        result.setConcurrentClients(concurrentClients);
        result.setCollectIndividualResults(collectIndividualResults);
        final long maxRequestsPerThread = Math.round((double)totalRequests / (double)concurrentClients);
        System.out.println("Requests to be sent per thread: " + maxRequestsPerThread);
        result.setTestUri(testUri);
        httpClient.setMaxRequestsQueuedPerDestination(Integer.MAX_VALUE);
        httpClient.start();
        statusUpdater.start();
        for (int loaderCount = 0; loaderCount < concurrentClients; loaderCount++) {
            loaderThreadPool.execute(new Runnable() {

                private AtomicLong responseReceived = new AtomicLong();

                @Override
                public void run() {
                    int count = 0;
                    try {
                        while (count++ <= maxRequestsPerThread && !stopped) {
                            final long start = System.nanoTime();
                            beforeSendRequest();
                            String uriWithId = String.format("%s?id=%d", testUri, Math.abs(idGenerator.nextLong()));
                            httpClient.newRequest(uriWithId).timeout(requestTimeoutMs, TimeUnit.MILLISECONDS).send(new Response.CompleteListener() {
                                @Override
                                public void onComplete(Result result) {
                                    processResponse(result, start);
                                    if (responseReceived.incrementAndGet() >= maxRequestsPerThread) {
                                        System.out.println("Loader thread: " + Thread.currentThread().getName()
                                                           + " got all responses. Stopping the loader.");
                                        finishingLatch.countDown();
                                    }
                                }
                            });
                        }
                        System.out.println("Loader thread: " + Thread.currentThread().getName() + " finished enqueing all requests to the selector. ");
                    } catch (Exception e) {
                        System.err.println("Error while sending a request from worker: " + Thread.currentThread().getName());
                        e.printStackTrace();
                    }
                }
            });
        }

        try {
            finishingLatch.await();
            System.out.println("Test finished, collecting results.");
        } catch (InterruptedException e) {
            System.err.println("Client interrupted while waiting for loader threads to return. Will try to generate result anyways.");
        }

        stopped = true;
        statusUpdater.stop();
        System.out.println("Stopped the Status updater.");
        try {
            httpClient.stop();
        } catch (Exception e) {
            System.out.println("Error while shutting down underlying http client.");
            e.printStackTrace();
        }
        System.out.println("Stopped the http client.");
        loaderThreadPool.shutdown();
        System.out.println("Shutdown the loader threadpool.");
        resultStatsCollector.calculateResult(result);
        System.out.println("Calculated stats about the test.");
        onCompleteHandler.run();
    }

    private void processResponse(Result result, long startTime) {
        if (stopped) {
            return;
        }

        final long end = System.nanoTime();
        long totalTimeInMillis = TimeUnit.MILLISECONDS.convert((end - startTime), TimeUnit.NANOSECONDS);

        resultStatsCollector.addResponseDetails(result, totalTimeInMillis);
        if (result.isSucceeded()) {
            if (collectIndividualResults) {
                this.result.getIndividualResults().add(getIndividualResult(result.getResponse(), totalTimeInMillis));
            }
        } else {
            Throwable requestFailure = result.getRequestFailure();
            if (null != requestFailure) {
                System.err.println("Test request failed with error: " + requestFailure.getMessage());
            } else {
                Throwable responseFailure = result.getResponseFailure();
                if (null != responseFailure) {
                    System.err.println("Failed to get test response. Error: " + responseFailure.getMessage());
                }
            }
        }
        long requestCount = this.requestCount.incrementAndGet();
        statusUpdater.onNewItem(requestCount);
    }

    private TestResult.IndividualResult getIndividualResult(Response response, long totalTime) {
        if (!collectIndividualResults) {
            return null;
        }
        TestResult.IndividualResult toReturn = new TestResult.IndividualResult();
        toReturn.setTotalTime(String.valueOf(totalTime));
        HttpFields headers = response.getHeaders();
        toReturn.setServerTime(headers.get("server_response_time"));
        toReturn.setLoadAvgPerCore(headers.get("load_avg_per_core"));
        return toReturn;
    }

    private void beforeSendRequest() {
    }

    public TestResult getResult() {
        return result;
    }

    public static class Builder {

        private AsyncIOClient client;

        public Builder() {
            this.client = new AsyncIOClient();
        }

        public Builder withMaxConnections(int maxConnections) {
            client.httpClient.setMaxConnectionsPerDestination(maxConnections);
            return this;
        }

        public Builder withConcurrentClients(int concurrentClients) {
            client.concurrentClients = concurrentClients;
            return this;
        }

        public Builder withTotalRequests(long requestCount) {
            client.totalRequests = requestCount;
            return this;
        }

        public Builder withRequestTimeoutMs(long timeoutInMs) {
            client.requestTimeoutMs = timeoutInMs;
            return this;
        }

        public Builder dontCollectIndividualResults() {
            client.collectIndividualResults = false;
            return this;
        }

        public Builder withTestUrl(String uri) {
            client.testUri = uri;
            return this;
        }

        public AsyncIOClient build() {
            return client;
        }
    }
}
