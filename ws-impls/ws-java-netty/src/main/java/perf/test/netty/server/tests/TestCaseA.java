package perf.test.netty.server.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.NettyUtils;
import perf.test.netty.PropertyNames;
import perf.test.netty.client.HttpClient;
import perf.test.netty.client.PoolExhaustedException;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class TestCaseA extends TestCaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(TestCaseA.class);

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
        String uri = String.format("/mock.json?numItems=%d&itemSize=%d&delay=%d&id=", numItems, itemSize, delay);
        if (logger.isDebugEnabled()) {
            logger.debug("Created a new uri: " + uri);
        }
        return uri;
    }

    private final AtomicLong inflightTests = new AtomicLong();

    private static final AttributeKey<Boolean> responseSent = new AttributeKey<Boolean>("response_sent_for_test");

    public TestCaseA(EventLoopGroup eventLoopGroup) throws PoolExhaustedException {
        super("testA", eventLoopGroup);
    }

    @Override
    protected void executeTestCase(final Channel channel, final boolean keepAlive, String id,
                                   final FullHttpResponse topLevelResponse) throws Throwable {
        channel.attr(responseSent).set(false);
        inflightTests.incrementAndGet();
        final ResponseCollector responseCollector = new ResponseCollector();

        final MoveForwardBarrier topLevelMoveFwdBarrier = new MoveForwardBarrier(2);

        CompletionListener callAListener =
                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_A_INDEX) {

                    @Override
                    protected void onResponseReceived() {
                        final MoveForwardBarrier callAMoveFwdBarrier = new MoveForwardBarrier(2);

                        CompletionListener callCListener =
                                new CompletionListener(channel, keepAlive, responseCollector,
                                                       ResponseCollector.RESPONSE_C_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier
                                                .shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(channel, keepAlive, topLevelResponse,
                                                                        responseCollector);
                                        }
                                    }
                                };

                        CompletionListener callDListener =
                                new CompletionListener(channel, keepAlive, responseCollector,
                                                       ResponseCollector.RESPONSE_D_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier
                                                .shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(channel, keepAlive, topLevelResponse,
                                                                        responseCollector);
                                        }
                                    }
                                };

                        get(channel.eventLoop().next(),
                            CALL_C_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX]
                                    .getResponseKey(),
                            callCListener);
                        get(channel.eventLoop().next(),
                            CALL_D_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX]
                                    .getResponseKey(),
                            callDListener);
                    }
                };

        CompletionListener callBListener =
                new CompletionListener(channel, keepAlive, responseCollector, ResponseCollector.RESPONSE_B_INDEX) {
                    @Override
                    protected void onResponseReceived() {
                        CompletionListener callEListener =
                                new CompletionListener(channel, keepAlive, responseCollector,
                                                       ResponseCollector.RESPONSE_E_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(channel, keepAlive, topLevelResponse,
                                                                        responseCollector);
                                        }
                                    }
                                };
                        get(channel.eventLoop().next(),
                            CALL_E_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_B_INDEX]
                                    .getResponseKey(),
                            callEListener);
                    }
                };
        get(channel.eventLoop().next(), CALL_A_URI_WITHOUT_ID + id, callAListener
        );
        get(channel.eventLoop().next(), CALL_B_URI_WITHOUT_ID + id, callBListener
        );
    }

    @Override
    protected long getTestsInFlight() {
        return inflightTests.get();
    }

    private void buildFinalResponseAndFinish(Channel channel, boolean keepAlive, FullHttpResponse topLevelResponse,
                                             ResponseCollector responseCollector) {
        ByteArrayOutputStream outputStream;
        try {
            outputStream = ServiceResponseBuilder.buildTestAResponse(jsonFactory, responseCollector.responses);
            // output to stream
            topLevelResponse.content().writeBytes(Unpooled.copiedBuffer(outputStream.toByteArray()));
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, topLevelResponse);
        } catch (IOException e) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            NettyUtils.createErrorResponse(jsonFactory, response, e.getMessage());
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
        } finally {
            if (channel.attr(responseSent).compareAndSet(false, true)) {
                inflightTests.decrementAndGet();
            }
        }
    }


    public static class ResponseCollector {

        private final BackendResponse[] responses = new BackendResponse[5];

        private static final int RESPONSE_A_INDEX = 0;
        private static final int RESPONSE_B_INDEX = 1;
        private static final int RESPONSE_C_INDEX = 2;
        private static final int RESPONSE_D_INDEX = 3;
        private static final int RESPONSE_E_INDEX = 4;
    }

    private static class MoveForwardBarrier {

        private final int expectedCalls;
        private final AtomicInteger responseReceivedCounter;

        private MoveForwardBarrier(int expectedCalls) {
            this.expectedCalls = expectedCalls;
            responseReceivedCounter = new AtomicInteger();
        }

        boolean shouldProceedOnResponse() {
            int responseCount = responseReceivedCounter.incrementAndGet();
            return responseCount >= expectedCalls;
        }
    }

    private abstract class CompletionListener implements HttpClient.ClientResponseHandler<FullHttpResponse> {

        private final Channel channel;
        private final boolean keepAlive;
        private final ResponseCollector responseCollector;
        private final int responseIndex;

        protected CompletionListener(Channel channel, boolean keepAlive, ResponseCollector responseCollector,
                                     int responseIndex) {
            this.channel = channel;
            this.keepAlive = keepAlive;
            this.responseCollector = responseCollector;
            this.responseIndex = responseIndex;
        }

        @Override
        public void onComplete(FullHttpResponse response) {
            HttpResponseStatus status = response.getStatus();
            if (status.equals(HttpResponseStatus.OK)) {
                ByteBuf responseContent = response.content();
                if (responseContent.isReadable()) {
                    String content = responseContent.toString(CharsetUtil.UTF_8);
                    try {
                        responseCollector.responses[responseIndex] = BackendResponse.fromJson(jsonFactory, content);
                        onResponseReceived();
                    } catch (Exception e) {
                        logger.error("Failed to parse the received backend response.", e);
                        response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
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
        public void onError(Throwable cause) {
            if (!channel.isActive()) {
                logger.error("Client completion listener got an exception when the server channel is disconnected. Nothing else to do.",
                             cause);
                return;
            }
            if (channel.attr(responseSent).compareAndSet(false, true)) {
                inflightTests.decrementAndGet();
            }
            HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            if (cause instanceof PoolExhaustedException) {
                status = HttpResponseStatus.SERVICE_UNAVAILABLE;
            }
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                                    HttpResponseStatus.INTERNAL_SERVER_ERROR);
            NettyUtils.createErrorResponse(status, jsonFactory, response, null != cause ? cause.getMessage() : "Unknown");
            NettyUtils.sendResponse(channel, keepAlive, jsonFactory, response);
        }

        protected abstract void onResponseReceived();
    }

}
