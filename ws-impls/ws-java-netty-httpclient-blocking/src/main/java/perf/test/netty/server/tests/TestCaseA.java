package perf.test.netty.server.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.SourceRequestState;
import perf.test.netty.client.PoolExhaustedException;
import perf.test.netty.server.RequestProcessingFailedException;
import perf.test.netty.server.RequestProcessingPromise;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class TestCaseA extends TestCaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(TestCaseA.class);

    public static final String CALL_A_URI_WITHOUT_ID = constructUri("A",
            PropertyNames.TestCaseACallANumItems.getValueAsInt(),
            PropertyNames.TestCaseACallAItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallAItemDelay.getValueAsInt());

    public static final String CALL_B_URI_WITHOUT_ID = constructUri("B",
            PropertyNames.TestCaseACallBNumItems.getValueAsInt(),
            PropertyNames.TestCaseACallBItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallBItemDelay.getValueAsInt());

    public static final String CALL_C_URI_WITHOUT_ID = constructUri("C",
            PropertyNames.TestCaseACallCNumItems.getValueAsInt(),
            PropertyNames.TestCaseACallCItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallCItemDelay.getValueAsInt());

    public static final String CALL_D_URI_WITHOUT_ID = constructUri("D",
            PropertyNames.TestCaseACallDNumItems.getValueAsInt(),
            PropertyNames.TestCaseACallDItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallDItemDelay.getValueAsInt());

    public static final String CALL_E_URI_WITHOUT_ID = constructUri("E",
            PropertyNames.TestCaseACallENumItems.getValueAsInt(),
            PropertyNames.TestCaseACallEItemSize.getValueAsInt(),
            PropertyNames.TestCaseACallEItemDelay.getValueAsInt());

    private static String constructUri(String type, int numItems, int itemSize, int delay) {
        String uri = String.format("/mock.json?type=%s&numItems=%d&itemSize=%d&delay=%d&id=", type, numItems, itemSize, delay);
        if (logger.isDebugEnabled()) {
            logger.debug("Created a new uri: " + uri);
        }
        return uri;
    }

    public TestCaseA(EventLoopGroup eventLoopGroup) throws PoolExhaustedException {
        super("testA", eventLoopGroup);
    }

    @Override
    protected void executeTestCase(final Channel channel, final boolean keepAlive, String id,
                                   final RequestProcessingPromise requestProcessingPromise) {

        final String reqId = SourceRequestState.instance().getRequestId(channel);

        final ResponseCollector responseCollector = new ResponseCollector();

        final MoveForwardBarrier topLevelMoveFwdBarrier = new MoveForwardBarrier(2);

        CompletionListener callAListener =
                new CompletionListener(responseCollector, ResponseCollector.RESPONSE_A_INDEX, requestProcessingPromise) {

                    @Override
                    protected void onResponseReceived() {
                        final MoveForwardBarrier callAMoveFwdBarrier = new MoveForwardBarrier(2);

                        CompletionListener callCListener =
                                new CompletionListener(responseCollector, ResponseCollector.RESPONSE_C_INDEX,
                                                       requestProcessingPromise) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier
                                                .shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(responseCollector, requestProcessingPromise);
                                        }
                                    }
                                };

                        CompletionListener callDListener =
                                new CompletionListener(responseCollector, ResponseCollector.RESPONSE_D_INDEX,
                                                       requestProcessingPromise) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier
                                                .shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(responseCollector, requestProcessingPromise);
                                        }
                                    }
                                };

                        get(reqId, channel.eventLoop().next(),
                            CALL_C_URI_WITHOUT_ID
                            + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX].getResponseKey(),
                            callCListener, requestProcessingPromise, ResponseCollector.RESPONSE_C_INDEX);
                        get(reqId, channel.eventLoop().next(),
                            CALL_D_URI_WITHOUT_ID
                            + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX].getResponseKey(),
                            callDListener, requestProcessingPromise, ResponseCollector.RESPONSE_D_INDEX);
                    }
                };

        CompletionListener callBListener =
                new CompletionListener(responseCollector, ResponseCollector.RESPONSE_B_INDEX,
                                       requestProcessingPromise) {
                    @Override
                    protected void onResponseReceived() {
                        CompletionListener callEListener =
                                new CompletionListener(responseCollector, ResponseCollector.RESPONSE_E_INDEX,
                                                       requestProcessingPromise) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish(responseCollector, requestProcessingPromise);
                                        }
                                    }
                                };
                        get(reqId, channel.eventLoop().next(),
                            CALL_E_URI_WITHOUT_ID
                            + responseCollector.responses[ResponseCollector.RESPONSE_B_INDEX].getResponseKey(),
                            callEListener, requestProcessingPromise, ResponseCollector.RESPONSE_E_INDEX);
                    }
                };
        get(reqId, channel.eventLoop().next(), CALL_A_URI_WITHOUT_ID + id, callAListener, requestProcessingPromise, ResponseCollector.RESPONSE_A_INDEX);
        get(reqId, channel.eventLoop().next(), CALL_B_URI_WITHOUT_ID + id, callBListener, requestProcessingPromise, ResponseCollector.RESPONSE_B_INDEX);
    }

    protected Future<FullHttpResponse> get(String reqId, EventExecutor eventExecutor, String path,
                                           GenericFutureListener<Future<FullHttpResponse>> responseHandler,
                                           final RequestProcessingPromise requestProcessingPromise, int callIndex) {
        if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
            requestProcessingPromise.checkpoint("Sending request for call index: " + callIndex);
        }
        return get(reqId, eventExecutor, path, responseHandler);
    }

    private static void buildFinalResponseAndFinish(ResponseCollector responseCollector,
                                                    Promise<FullHttpResponse> requestProcessingPromise) {
        ByteArrayOutputStream outputStream;
        try {
            outputStream = ServiceResponseBuilder.buildTestAResponse(jsonFactory, responseCollector.responses);
            ByteBuf content = Unpooled.copiedBuffer(outputStream.toByteArray());
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
            requestProcessingPromise.trySuccess(response);
        } catch (IOException e) {
            requestProcessingPromise.tryFailure(new RequestProcessingFailedException(HttpResponseStatus.INTERNAL_SERVER_ERROR, e));
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

    private abstract static class CompletionListener implements GenericFutureListener<Future<FullHttpResponse>> {

        private final ResponseCollector responseCollector;
        private final int responseIndex;
        private final RequestProcessingPromise topLevelRequestCompletionPromise;

        protected CompletionListener(ResponseCollector responseCollector, int responseIndex, RequestProcessingPromise topLevelRequestCompletionPromise) {
            this.responseCollector = responseCollector;
            this.responseIndex = responseIndex;
            this.topLevelRequestCompletionPromise = topLevelRequestCompletionPromise;
        }

        @Override
        public void operationComplete(Future<FullHttpResponse> future) throws Exception {
            if (future.isSuccess()) {
                if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                    topLevelRequestCompletionPromise.checkpoint("Call success for response index: " + responseIndex);
                }
                FullHttpResponse response = future.get();
                HttpResponseStatus status = response.getStatus();
                if (status.equals(HttpResponseStatus.OK)) {
                    ByteBuf responseContent = response.content();
                    if (responseContent.isReadable()) {
                        String content = responseContent.toString(CharsetUtil.UTF_8);
                        responseContent.release();
                        try {
                            responseCollector.responses[responseIndex] = BackendResponse.fromJson(jsonFactory, content);
                            onResponseReceived();
                        } catch (Exception e) {
                            logger.error("Failed to parse the received backend response.", e);
                            topLevelRequestCompletionPromise.tryFailure(new RequestProcessingFailedException(HttpResponseStatus.INTERNAL_SERVER_ERROR, e));
                        }
                    }
                } else {
                    if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                        topLevelRequestCompletionPromise.checkpoint("Call failed for response index: " + responseIndex + ", error: " + future.cause());
                    }
                    topLevelRequestCompletionPromise.tryFailure(new RequestProcessingFailedException(status));
                }
            } else {
                HttpResponseStatus status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                Throwable cause = future.cause();
                if (cause instanceof PoolExhaustedException) {
                    status = HttpResponseStatus.SERVICE_UNAVAILABLE;
                }
                topLevelRequestCompletionPromise.tryFailure(new RequestProcessingFailedException(status, cause));

            }
        }

        protected abstract void onResponseReceived();
    }

}
