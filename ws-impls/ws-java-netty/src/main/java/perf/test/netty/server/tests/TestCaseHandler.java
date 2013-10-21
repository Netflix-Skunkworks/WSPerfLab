package perf.test.netty.server.tests;

import com.google.common.base.Preconditions;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.codehaus.jackson.JsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.NettyUtils;
import perf.test.netty.PropertyNames;
import perf.test.netty.client.HttpClient;
import perf.test.netty.client.HttpClientFactory;
import perf.test.netty.client.LBAwareHttpClientImpl;
import perf.test.netty.client.PoolExhaustedException;
import perf.test.netty.client.RoundRobinLB;
import perf.test.netty.server.StatusRetriever;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
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

    protected TestCaseHandler(String testCaseName, EventLoopGroup eventLoopGroup) throws PoolExhaustedException {
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

    public void processRequest(Channel channel, boolean keepAlive, HttpRequest request, QueryStringDecoder qpDecoder) {
        Map<String,List<String>> parameters = qpDecoder.parameters();
        List<String> id = parameters.get("id");
        FullHttpResponse response;
        if (null == id || id.isEmpty()) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            NettyUtils.createErrorResponse(jsonFactory, response, "query parameter id not provided.");
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
        } else {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            try {
                executeTestCase(channel, keepAlive, id.get(0), response);
            } catch (Throwable throwable) {
                logger.error("Test case execution threw an exception.", throwable);
                NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
            }
        }
    }

    protected abstract void executeTestCase(Channel channel, boolean keepAlive, String id, FullHttpResponse response) throws Throwable;

    public void dispose() {
        clientFactory.shutdown();
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    protected void get(EventExecutor eventExecutor, String path,
                       final HttpClient.ClientResponseHandler<FullHttpResponse> responseHandler) {
        Preconditions.checkNotNull(eventExecutor, "Event executor can not be null");
        String basePath = PropertyNames.MockBackendContextPath.getValueAsString();
        path = basePath + path;
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
        try {
            final String fullPath = path;
            httpClient.execute(eventExecutor, request, responseHandler).addListener(new GenericFutureListener<Future<FullHttpResponse>>() {

                @Override
                public void operationComplete(Future<FullHttpResponse> future) throws Exception {
                    if (!future.isSuccess()) {
                        logger.error("Failed to execute backend get request: " + fullPath);
                        responseHandler.onError(future.cause());
                    }

                }
            });
        } catch (PoolExhaustedException e) {
            responseHandler.onError(e);
        }
    }

    public void populateStatus(StatusRetriever.Status statusToPopulate) {
        StatusRetriever.TestCaseStatus testCaseStatus = new StatusRetriever.TestCaseStatus();
        httpClient.populateStatus(testCaseStatus);
        testCaseStatus.setInflightTests(getTestsInFlight());
        statusToPopulate.addTestStatus(testCaseName, testCaseStatus);
    }

    protected abstract long getTestsInFlight();
}
