package perf.test.netty.server.tests;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.codehaus.jackson.JsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.RequestProcessingFailedException;
import perf.test.netty.server.RequestProcessingPromise;
import perf.test.netty.server.ServerHandler;
import perf.test.netty.server.StatusRetriever;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 * @author mhawthorne
 */
public abstract class TestCaseHandler {

    private static final Pattern HOSTS_SPLITTER = Pattern.compile(",");
    private final Logger logger = LoggerFactory.getLogger(TestCaseHandler.class);

    private final String testCaseName;
    protected final static JsonFactory jsonFactory = new JsonFactory();

    private final HttpClient client;

    private final AtomicLong testWithErrors = new AtomicLong();
    private final AtomicLong inflightTests = new AtomicLong();
    private final AtomicLong requestRecvCount = new AtomicLong();

    private final HostSelector hostSelector;

    private static interface HostSelector {
        String next();
    }

    private static final HostSelector newHostSelector(String... hosts) {
        return new RandomHostSelector(hosts);
    }

    private static final class RoundRobinHostSelector implements HostSelector {

        private final String[] hosts;
        private final int hostCount;

        // trying to store the index in a thread local so that multiple threads won't contend
        // I can't tell if this is a stupid way to handle this problem or not, my brain isn't working today
        private static final ThreadLocal<Integer> localIndex = new ThreadLocal<Integer>() {
            @Override
            protected Integer initialValue() {
                return 0;
            }
        };

        RoundRobinHostSelector(String ... hosts) {
            this.hosts = hosts;
            this.hostCount = hosts.length;
        }

        public String next() {
            int idx = localIndex.get();
            if(idx == hostCount)
                idx = 0;
            final String host = hosts[idx];
            localIndex.set(++idx);
            return host;
        }
    }

    private static final class RandomHostSelector implements HostSelector {

        private final String[] hosts;
        private final int hostCount;

        RandomHostSelector(String ... hosts) {
            this.hosts = hosts;
            this.hostCount = hosts.length;
        }

        @Override
        public String next() {
            final int idx = (int) Math.floor(Math.random() * this.hostCount);
            return this.hosts[idx];
        }
    }

    protected TestCaseHandler(String testCaseName, EventLoopGroup eventLoopGroup) {
        this.testCaseName = testCaseName;

        String hosts = PropertyNames.MockBackendHost.getValueAsString();
        String[] splitHosts = HOSTS_SPLITTER.split(hosts);
        int serverPort = PropertyNames.MockBackendPort.getValueAsInt();

        this.hostSelector = newHostSelector(splitHosts);

        final RequestConfig reqConfig = RequestConfig.custom()
            .setConnectTimeout(PropertyNames.ClientConnectTimeout.getValueAsInt())
            .setSocketTimeout(PropertyNames.ClientSocketTimeout.getValueAsInt())
            // setting an aggressive pool timeout to avoid queueing
            .setConnectionRequestTimeout(PropertyNames.ClientConnectionRequestTimeout.getValueAsInt())
            .build();

        this.client = HttpClients.custom()
            .setDefaultRequestConfig(reqConfig)
            .setConnectionManager(new PoolingHttpClientConnectionManager())
            .setMaxConnTotal(PropertyNames.ClientMaxConnectionsTotal.getValueAsInt())
            .build();
    }

    public void processRequest(Channel channel, HttpRequest request, QueryStringDecoder qpDecoder,
                               RequestProcessingPromise requestProcessingPromise) {
        inflightTests.incrementAndGet();
        requestRecvCount.incrementAndGet();
        requestProcessingPromise.addListener(new GenericFutureListener<Future<? super FullHttpResponse>>() {
            @Override
            public void operationComplete(Future<? super FullHttpResponse> future) throws Exception {
                inflightTests.decrementAndGet();
            }
        });

        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        Map<String,List<String>> parameters = qpDecoder.parameters();
        List<String> id = parameters.get("id");
        if (null == id || id.isEmpty()) {
            requestProcessingPromise.tryFailure(new RequestProcessingFailedException(HttpResponseStatus.BAD_REQUEST, new IllegalArgumentException( "query parameter id not provided.")));
        } else {
            try {
                String thisId = id.get(0);
                requestProcessingPromise.setTestCaseId(thisId);
                executeTestCase(channel, keepAlive, thisId, requestProcessingPromise);
            } catch (Throwable throwable) {
                logger.error("Test case execution failed.", throwable);
                testWithErrors.incrementAndGet();
                requestProcessingPromise.tryFailure(new RequestProcessingFailedException(HttpResponseStatus.INTERNAL_SERVER_ERROR,throwable));
            }
        }
    }

    protected abstract void executeTestCase(Channel channel, boolean keepAlive, String id,
                                            RequestProcessingPromise requestProcessingPromise);

    public void dispose() {
//        clientFactory.shutdown();
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    protected Future<FullHttpResponse> get(EventExecutor eventExecutor, String path,
                                               final GenericFutureListener<Future<FullHttpResponse>> responseHandler) {
        return this.httpClientGet(eventExecutor, path, responseHandler);
    }

    private Future<FullHttpResponse> httpClientGet(EventExecutor eventExecutor, String path,
                                           final GenericFutureListener<Future<FullHttpResponse>> responseHandler) {
        Preconditions.checkNotNull(eventExecutor, "Event executor can not be null");
        String basePath = PropertyNames.MockBackendContextPath.getValueAsString();
        path = basePath + path;

        try {
            final String host = this.hostSelector.next();

            final String uri = "http://" + host + ":" +
                PropertyNames.MockBackendPort.getValueAsString() + path;
            logger.debug("backend request URI: " + uri);
            final HttpUriRequest originReq = new HttpGet(uri);
            final HttpResponse originRes = (HttpResponse) this.client.execute(originReq);
            final DefaultPromise<FullHttpResponse> promise = new DefaultPromise<FullHttpResponse>(eventExecutor);
            promise.addListener(responseHandler);


            final ByteBuf nettyResBytes = Unpooled.buffer();

            final InputStream originResStream = originRes.getEntity().getContent();
            final byte[] b = new byte[1024];
            int read = -1;
            while((read = originResStream.read(b)) != -1) {
                nettyResBytes.writeBytes(b, 0, read);
            }

            final DefaultFullHttpResponse nettyRes = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(originRes.getStatusLine().getStatusCode()),
                nettyResBytes);
            promise.trySuccess(nettyRes);

            return promise;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    private Future<FullHttpResponse> nettyGet(EventExecutor eventExecutor, String path,
//                                           final GenericFutureListener<Future<FullHttpResponse>> responseHandler) {
//        Preconditions.checkNotNull(eventExecutor, "Event executor can not be null");
//        String basePath = PropertyNames.MockBackendContextPath.getValueAsString();
//        path = basePath + path;
//        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
//        return httpClient.execute(eventExecutor, request).addListener(responseHandler);
//    }

    public void populateStatus(StatusRetriever.Status statusToPopulate) {
        StatusRetriever.TestCaseStatus testCaseStatus = new StatusRetriever.TestCaseStatus();

//        httpClient.populateStatus(testCaseStatus);

        ServerHandler.populateStatus(testCaseStatus);
        testCaseStatus.setInflightTests(inflightTests.get());
        testCaseStatus.setRequestRecvCount(requestRecvCount.get());
        testCaseStatus.setTestWithErrors(testWithErrors.get());
        statusToPopulate.addTestStatus(testCaseName, testCaseStatus);
    }

    public void populateTrace(StringBuilder traceBuilder) {
//        httpClient.populateTrace(traceBuilder);
    }

}
