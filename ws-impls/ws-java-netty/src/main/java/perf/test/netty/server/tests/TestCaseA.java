package perf.test.netty.server.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import perf.test.netty.NettyUtils;
import perf.test.netty.PropertyNames;
import perf.test.netty.client.NettyClientPool;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;
import perf.test.utils.URLSelector;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class TestCaseA extends TestCaseHandler {

    private static Logger logger = LoggerFactory.getLogger(TestCaseA.class);

    public static final String CALL_A_URI_WITHOUT_ID = constructUri(
            PropertyNames.TestCaseACallANumItems.getValueAsInt(),
            PropertyNames.TestCaseACallAItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallAItemDelay.getValueAsInt());

    public static final String CALL_B_URI_WITHOUT_ID = constructUri(
            PropertyNames.TestCaseACallBNumItems.getValueAsInt(),
            PropertyNames.TestCaseACallBItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallBItemDelay.getValueAsInt());

    public static final String CALL_C_URI_WITHOUT_ID = constructUri(
            PropertyNames.TestCaseACallCNumItems.getValueAsInt(),
            PropertyNames.TestCaseACallCItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallCItemDelay.getValueAsInt());

    public static final String CALL_D_URI_WITHOUT_ID = constructUri(
            PropertyNames.TestCaseACallDNumItems.getValueAsInt(),
            PropertyNames.TestCaseACallDItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallDItemDelay.getValueAsInt());

    public static final String CALL_E_URI_WITHOUT_ID = constructUri(
            PropertyNames.TestCaseACallENumItems.getValueAsInt(),
            PropertyNames.TestCaseACallEItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallEItemDelay.getValueAsInt());

    private static String constructUri(int numItems, int itemSize, int delay) {
        String uri = String.format("%s/mock.json?numItems=%d&itemSize=%d&delay=%d&id=",
                URLSelector.chooseURLBase(),
                numItems, itemSize, delay);
        if (logger.isDebugEnabled()) {
            logger.debug("Created a new uri: " + uri);
        }
        return uri;
    }

    private AtomicLong inflightTests = new AtomicLong();

    public TestCaseA() throws InterruptedException {
        super("testA");
    }

    @Override
    protected void executeTestCase(final Channel channel, final boolean keepAlive, String id, final HttpResponse topLevelResponse) throws Throwable {
        inflightTests.incrementAndGet();
        final ResponseCollector responseCollector = new ResponseCollector();

        final MoveForwardBarrier topLevelMoveFwdBarrier = new MoveForwardBarrier("top", 2);

        CompletionListener callAListener =
                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_A_INDEX) {

                    @Override
                    protected void onResponseReceived() {
                        final MoveForwardBarrier callAMoveFwdBarrier = new MoveForwardBarrier("callA", 2);

                        CompletionListener callCListener =
                                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_C_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(channel, keepAlive, topLevelResponse, responseCollector);
                                        }
                                    }
                                };

                        CompletionListener callDListener =
                                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_D_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(channel, keepAlive, topLevelResponse, responseCollector);
                                        }
                                    }
                                };

                        get(CALL_C_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX].getResponseKey(),
                            callCListener, topLevelResponse, channel, keepAlive);
                        get(CALL_D_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX] .getResponseKey(),
                            callDListener, topLevelResponse, channel, keepAlive);
                    }
                };

        CompletionListener callBListener =
                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_B_INDEX) {
                    @Override
                    protected void onResponseReceived() {
                        CompletionListener callEListener =
                                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_E_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(channel, keepAlive, topLevelResponse, responseCollector);
                                        }
                                    }
                                };
                        get(CALL_E_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_B_INDEX]
                                .getResponseKey(),
                            callEListener, topLevelResponse, channel, keepAlive);
                    }
                };
        get(CALL_A_URI_WITHOUT_ID + id, callAListener, topLevelResponse, channel, keepAlive);
        get(CALL_B_URI_WITHOUT_ID + id, callBListener, topLevelResponse, channel, keepAlive);
    }

    @Override
    protected long getTestsInFlight() {
        return inflightTests.get();
    }

    private void buildFinalResponseAndFinish(Channel channel, boolean keepAlive, HttpResponse topLevelResponse,
                                             ResponseCollector responseCollector) {
        ByteArrayOutputStream outputStream;
        try {
            outputStream = ServiceResponseBuilder.buildTestAResponse(jsonFactory, responseCollector.responses);
            // output to stream
            topLevelResponse.setContent(ChannelBuffers.copiedBuffer(outputStream.toByteArray()));
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, topLevelResponse);
        } catch (IOException e) {
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            NettyUtils.createErrorResponse(jsonFactory, response, e.getMessage());
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
        } finally {
            inflightTests.decrementAndGet();
        }
    }


    public static class ResponseCollector {

        private BackendResponse[] responses = new BackendResponse[5];

        private static final int RESPONSE_A_INDEX = 0;
        private static final int RESPONSE_B_INDEX = 1;
        private static final int RESPONSE_C_INDEX = 2;
        private static final int RESPONSE_D_INDEX = 3;
        private static final int RESPONSE_E_INDEX = 4;
    }

    private static class MoveForwardBarrier {

        private final String name;
        private final int expectedCalls;
        private AtomicInteger responseReceivedCounter;

        private MoveForwardBarrier(String name, int expectedCalls) {
            this.name = name;
            this.expectedCalls = expectedCalls;
            responseReceivedCounter = new AtomicInteger();
        }

        boolean shouldProceedOnResponse() {
            int responseCount = responseReceivedCounter.incrementAndGet();
            return responseCount >= expectedCalls;
        }
    }

    private static abstract class CompletionListener implements NettyClientPool.ClientCompletionListener {

        private final Channel channel;
        private final boolean keepAlive;
        private final ResponseCollector responseCollector;
        private final int responseIndex;

        public CompletionListener(Channel channel, boolean keepAlive, ResponseCollector responseCollector,
                                  int responseIndex) {
            this.channel = channel;
            this.keepAlive = keepAlive;
            this.responseCollector = responseCollector;
            this.responseIndex = responseIndex;
        }

        @Override
        public void onComplete(HttpResponse response) {
            HttpResponseStatus status = response.getStatus();
            if (status.equals(HttpResponseStatus.OK)) {
                ChannelBuffer responseContent = response.getContent();
                if (responseContent.readable()) {
                    String content = responseContent.toString(CharsetUtil.UTF_8);
                    try {
                        responseCollector.responses[responseIndex] = BackendResponse.fromJson(jsonFactory, content);
                        onResponseReceived();
                    } catch (Exception e) {
                        logger.error("Failed to parse the received backend response.", e);
                        response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                                           HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        NettyUtils.createErrorResponse(jsonFactory, response, e.getMessage());
                        NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
                    }
                }
            } else {
                NettyUtils.createErrorResponse(jsonFactory, response, "Backend server returned response status: " + status);
                NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
            }
        }

        @Override
        public void onError(ExceptionEvent exceptionEvent) {
            if (!channel.isConnected()) {
                logger.error("Client completion listener got an exception when the server channel is disconnected. Nothing else to do.", exceptionEvent.getCause());
                return;
            }
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
            Throwable cause = exceptionEvent.getCause();
            NettyUtils.createErrorResponse(jsonFactory, response, (null != cause) ? cause.getMessage() : "Unknown");
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
        }

        protected abstract void onResponseReceived();
    }

}
