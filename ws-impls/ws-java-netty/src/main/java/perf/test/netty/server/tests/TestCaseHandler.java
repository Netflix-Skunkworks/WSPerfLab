package perf.test.netty.server.tests;

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
import perf.test.netty.client.NettyClient;
import perf.test.netty.client.NettyClientPool;

import java.net.URI;
import java.util.List;
import java.util.Map;

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
                                         PropertyNames.MockBackendPort.getValueAsInt(),
                                         PropertyNames.MockBackendHost.getValueAsString());
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

    protected void get(String path, NettyClient.ClientCompletionListener listener, HttpResponse topLevelResponse,
                       Channel channel, boolean keepAlive) {
        String basePath = PropertyNames.MockBackendContextPath.getValueAsString();
        path = basePath + path;
        NettyClient nextAvailableClient;
        try {
            nextAvailableClient = clientPool.getNextAvailableClient();
            if (null != nextAvailableClient) {
                nextAvailableClient.get(new URI(path), listener);
            } else {
                String msg = "Backend connections exhausted. Failed to execute backend get request: " + path;
                logger.error(msg);
                NettyUtils.createErrorResponse(jsonFactory, topLevelResponse, msg);
                NettyUtils.sendResponse(channel, keepAlive, jsonFactory, topLevelResponse);
            }
        } catch (Throwable throwable) {
            logger.error("Failed to execute backend get request: " + path);
            NettyUtils.createErrorResponse(jsonFactory, topLevelResponse, throwable.getMessage());
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, topLevelResponse);
        }
    }
}
