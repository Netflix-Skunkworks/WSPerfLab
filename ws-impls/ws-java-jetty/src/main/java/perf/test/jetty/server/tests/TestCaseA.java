package perf.test.jetty.server.tests;

import com.sun.istack.internal.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.jetty.PropertyNames;
import perf.test.utils.BackendResponse;
import perf.test.utils.ServiceResponseBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Nitesh Kant
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

    private HttpClient backendClient;

    public TestCaseA() throws Exception {
        super("testA");
        backendClient = new HttpClient() {
            @Override
            public org.eclipse.jetty.client.api.Request newRequest(String uri) {
                return super.newRequest(uri);
            }
        };
        backendClient.setConnectTimeout(PropertyNames.ClientConnectTimeout.getValueAsInt());
        backendClient.setDispatchIO(false); // We want to execute in the selector thread as we don't do any blocking work.
        backendClient.setMaxConnectionsPerDestination(PropertyNames.MockBackendMaxConnectionsPerTest.getValueAsInt());
        backendClient.start();
    }

    @Override
    protected void doExecute(String id, Request baseRequest, final HttpServletResponse topLevelResponse,
            final Continuation continuation, final Runnable onCompleteHandler)
            throws InterruptedException, ExecutionException, TimeoutException {

        // The below is why you should use RxJava
        final ResponseCollector responseCollector = new ResponseCollector();

        final MoveForwardBarrier topLevelMoveFwdBarrier = new MoveForwardBarrier("top", 2);

        JettyClientResponseListener callAListener =
                new JettyClientResponseListener(continuation, topLevelResponse, onCompleteHandler,
                                                responseCollector, ResponseCollector.RESPONSE_A_INDEX) {
                    @Override
                    protected void onResponseReceived() {
                        final MoveForwardBarrier callAMoveFwdBarrier = new MoveForwardBarrier("callA", 2);

                        JettyClientResponseListener callCListener =
                                new JettyClientResponseListener(continuation, topLevelResponse, onCompleteHandler,
                                                                responseCollector,
                                                                ResponseCollector.RESPONSE_C_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish();
                                        }
                                    }
                                };

                        JettyClientResponseListener callDListener =
                                new JettyClientResponseListener(continuation, topLevelResponse, onCompleteHandler,
                                                                responseCollector,
                                                                ResponseCollector.RESPONSE_D_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (callAMoveFwdBarrier.shouldProceedOnResponse() && topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish();
                                        }
                                    }
                                };

                        backendClient.newRequest(CALL_C_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX].getResponseKey()).send(callCListener);
                        backendClient.newRequest(CALL_D_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX].getResponseKey()).send(callDListener);
                    }
                };

        JettyClientResponseListener callBListener =
                new JettyClientResponseListener(continuation, topLevelResponse, onCompleteHandler,
                                                responseCollector, ResponseCollector.RESPONSE_B_INDEX) {
                    @Override
                    protected void onResponseReceived() {
                        JettyClientResponseListener callEListener =
                                new JettyClientResponseListener(continuation, topLevelResponse, onCompleteHandler,
                                                                responseCollector,
                                                                ResponseCollector.RESPONSE_E_INDEX) {

                                    @Override
                                    protected void onResponseReceived() {
                                        if (topLevelMoveFwdBarrier.shouldProceedOnResponse()) {
                                            buildFinalResponseAndFinish();
                                        }
                                    }
                                };
                        backendClient.newRequest(CALL_E_URI_WITHOUT_ID + responseCollector.responses[ResponseCollector.RESPONSE_B_INDEX].getResponseKey()).send(callEListener);
                    }
                };

        backendClient.newRequest(CALL_A_URI_WITHOUT_ID + id).send(callAListener);
        backendClient.newRequest(CALL_B_URI_WITHOUT_ID + id).send(callBListener);
    }

    private void handleErrorFromExtCalls(Continuation continuation, HttpServletResponse topLevelResponse,
            Runnable onCompleteHandler, String callName, @Nullable String message) {
        if (continuation.isSuspended()) {
            // Fail fast
            topLevelResponse.setStatus(500);
            topLevelResponse.addHeader(PropertyNames.ErrorHeaderName.getValueAsString(),
                                       "Testcase A " + callName + " failed. Error: " + String.valueOf(message)); // do addHeader here so that we get all errors till the continuation resumes.
            onCompleteHandler.run();
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Testcase A, " + callName + " failed but some other call failed before, ignoring this failure.");
            }
        }
    }

    private boolean isSuccess(Result response) {
        if (response.isFailed()) {
            return false;
        }

        int status = response.getResponse().getStatus();
        return status >= 200 && status < 300;
    }

    private static String constructUri(int numItems, int itemSize, int delay) {
        String uri = String.format("http://%s:%d%s/mock.json?numItems=%d&itemSize=%d&delay=%d&id=",
                                      PropertyNames.MockBackendHost.getValueAsString(),
                                      PropertyNames.MockBackendPort.getValueAsInt(),
                                      PropertyNames.MockBackendContextPath.getValueAsString(), numItems, itemSize,
                                      delay);
        if (logger.isDebugEnabled()) {
            logger.debug("Created a new uri: " + uri);
        }
        return uri;
    }

    @Override
    public void dispose() throws Exception {
        backendClient.stop();
    }

    private abstract class JettyClientResponseListener extends BufferingResponseListener {

        protected final Continuation continuation;
        protected final HttpServletResponse topLevelResponse;
        protected final Runnable eventualCompletionHandler;
        protected final ResponseCollector responseCollector;
        protected final int responseCollectorIndex;


        public JettyClientResponseListener(Continuation continuation, HttpServletResponse topLevelResponse,
                                           Runnable eventualCompletionHandler, ResponseCollector responseCollector,
                                           int responseCollectorIndex) {
            super(PropertyNames.TestCaseAResponseBufferMaxSize.getValueAsInt());
            this.continuation = continuation;
            this.topLevelResponse = topLevelResponse;
            this.eventualCompletionHandler = eventualCompletionHandler;
            this.responseCollector = responseCollector;
            this.responseCollectorIndex = responseCollectorIndex;
        }


        @Override
        public void onComplete(Result result) {
            if (isSuccess(result)) {
                byte[] content = getContent();
                try {
                    responseCollector.responses[responseCollectorIndex] = BackendResponse.fromJson(jsonFactory, content);
                    onResponseReceived();
                } catch (Exception e) {
                    logger.error("Backend response parsing failed.", e);
                    handleErrorFromExtCalls(continuation, topLevelResponse, eventualCompletionHandler, "Call A",
                                            e.getMessage());
                }
            } else {
                handleErrorFromExtCalls(continuation, topLevelResponse, eventualCompletionHandler, "Call A", null);
            }
        }

        protected void buildFinalResponseAndFinish() {
            try {
                ByteArrayOutputStream byteArrayOutputStream =
                        ServiceResponseBuilder.buildTestAResponse(jsonFactory, responseCollector.responses);
                topLevelResponse.getWriter().write(byteArrayOutputStream.toString());
                if (logger.isDebugEnabled()) {
                    logger.debug("Served final response.");
                }
            } catch (IOException e) {
                logger.error("Error serializing final response.", e);
                handleErrorFromExtCalls(continuation, topLevelResponse, eventualCompletionHandler, "Call C", e.getMessage());
            }
            eventualCompletionHandler.run();
        }

        protected abstract void onResponseReceived();
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
}
