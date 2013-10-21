package perf.test.netty.server.tests;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import perf.test.netty.NettyUtils;
import perf.test.netty.PropertyNames;
import perf.test.netty.client.NettyClientPool;
import perf.test.netty.server.StatusRetriever;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public abstract class TestCaseHandler {

    private Logger logger = LoggerFactory.getLogger(TestCaseHandler.class);

    private final String testCaseName;
    protected final static JsonFactory jsonFactory = new JsonFactory();
    protected final NettyClientPool clientPool;

    protected TestCaseHandler(String testCaseName) throws InterruptedException {
        this.testCaseName = testCaseName;
        clientPool = new NettyClientPool(PropertyNames.MockBackendMaxConnectionsPerTest.getValueAsInt(),
                                         PropertyNames.MockBackendMaxConnectionsPerTest.getValueAsInt(),
                                         PropertyNames.MockBackendMaxBacklog.getValueAsInt());
    }

    public void processRequest(Channel channel, boolean keepAlive, HttpRequest request, QueryStringDecoder qpDecoder) {
        Map<String,List<String>> parameters = qpDecoder.getParameters();
        List<String> id = parameters.get("id");
        HttpResponse response;
        if (null == id || id.isEmpty()) {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            NettyUtils.createErrorResponse(jsonFactory, response, "query parameter id not provided.");
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
        } else {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            try {
                executeTestCase(channel, keepAlive, id.get(0), response);
            } catch (Throwable throwable) {
                logger.error("Test case execution threw an exception.", throwable);
                NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
            }
        }
    }

    protected abstract void executeTestCase(Channel channel, boolean keepAlive, String id, HttpResponse response) throws Throwable;

    public void dispose() {
        clientPool.shutdown();
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    protected void get(String path, NettyClientPool.ClientCompletionListener listener, HttpResponse topLevelResponse,
                       Channel channel, boolean keepAlive) {
        // path = basePath + path;
        path = null;
        try {
            clientPool.sendGetRequest(new URI(path), listener);
        } catch (Exception e) {
            logger.error("Failed to execute backend get request: " + path);
            NettyUtils.createErrorResponse(jsonFactory, topLevelResponse, e.getMessage());
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, topLevelResponse);
        }
    }

    public void populateStatus(StatusRetriever.Status statusToPopulate) {
        StatusRetriever.TestCaseStatus testCaseStatus = new StatusRetriever.TestCaseStatus();
        clientPool.populateStatus(testCaseStatus);
        testCaseStatus.setInflightTests(getTestsInFlight());
        statusToPopulate.addTestStatus(testCaseName, testCaseStatus);
    }

    protected abstract long getTestsInFlight();
}
