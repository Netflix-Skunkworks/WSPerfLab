package perf.test.netty.server.tests;

import com.google.common.base.Preconditions;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.codehaus.jackson.JsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.client.HttpClient;
import perf.test.netty.client.HttpClientFactory;
import perf.test.netty.client.LBAwareHttpClientImpl;
import perf.test.netty.client.PoolExhaustedException;
import perf.test.netty.client.RoundRobinLB;
import perf.test.netty.server.RequestProcessingFailedException;
import perf.test.netty.server.RequestProcessingPromise;
import perf.test.netty.server.ServerHandler;
import perf.test.netty.server.StatusRetriever;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public abstract class TestCaseHandler {

    private static final Pattern HOSTS_SPLITTER = Pattern.compile(",");
    private final Logger logger = LoggerFactory.getLogger(TestCaseHandler.class);

    private final String testCaseName;
    protected final static JsonFactory jsonFactory = new JsonFactory();
    protected final HttpClientFactory clientFactory;
    private final HttpClient<FullHttpResponse,FullHttpRequest> httpClient;
    private final AtomicLong testWithErrors = new AtomicLong();
    private final AtomicLong inflightTests = new AtomicLong();
    private final AtomicLong requestRecvCount = new AtomicLong();

    protected TestCaseHandler(String testCaseName, EventLoopGroup eventLoopGroup) {
        this.testCaseName = testCaseName;
        clientFactory = new HttpClientFactory(null, eventLoopGroup);
        String hosts = PropertyNames.MockBackendHost.getValueAsString();
        String[] splittedHosts = HOSTS_SPLITTER.split(hosts);
        int serverPort = PropertyNames.MockBackendPort.getValueAsInt();
        if (splittedHosts.length > 1) {
            RoundRobinLB<FullHttpRequest> lb = new RoundRobinLB<FullHttpRequest>(splittedHosts,
                                                                                 serverPort);
            httpClient = new LBAwareHttpClientImpl(lb, clientFactory);
        } else {
            httpClient = clientFactory.getHttpClient(new InetSocketAddress(hosts, serverPort));
        }
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
        clientFactory.shutdown();
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    protected Future<FullHttpResponse> get(EventExecutor eventExecutor, String path,
                                           final GenericFutureListener<Future<FullHttpResponse>> responseHandler) {
        Preconditions.checkNotNull(eventExecutor, "Event executor can not be null");
        String basePath = PropertyNames.MockBackendContextPath.getValueAsString();
        path = basePath + path;
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        return httpClient.execute(eventExecutor, request).addListener(responseHandler);
    }

    public void populateStatus(StatusRetriever.Status statusToPopulate) {
        StatusRetriever.TestCaseStatus testCaseStatus = new StatusRetriever.TestCaseStatus();
        httpClient.populateStatus(testCaseStatus);
        ServerHandler.populateStatus(testCaseStatus);
        testCaseStatus.setInflightTests(inflightTests.get());
        testCaseStatus.setRequestRecvCount(requestRecvCount.get());
        testCaseStatus.setTestWithErrors(testWithErrors.get());
        statusToPopulate.addTestStatus(testCaseName, testCaseStatus);
    }

    public void populateTrace(StringBuilder traceBuilder) {
        httpClient.populateTrace(traceBuilder);
    }
}
