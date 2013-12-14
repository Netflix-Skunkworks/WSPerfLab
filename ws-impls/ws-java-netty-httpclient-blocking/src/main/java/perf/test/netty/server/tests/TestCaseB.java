package perf.test.netty.server.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.RequestProcessingFailedException;
import perf.test.netty.server.RequestProcessingPromise;
import perf.test.utils.ServiceResponseBuilder;
import perf.test.utils.netty.SourceRequestState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author mhawthorne
 */
public class TestCaseB extends TestCaseHandler {

    public static final String CALL_A_URI_WITHOUT_ID = constructUri("A",
        PropertyNames.TestCaseACallANumItems.getValueAsInt(),
        PropertyNames.TestCaseACallAItemSize.getValueAsInt(),
        PropertyNames.TestCaseACallAItemDelay.getValueAsInt());


    public TestCaseB(EventLoopGroup eventLoopGroup) {
        super("testB", eventLoopGroup);
    }

    @Override
    protected void executeTestCase(Channel channel, EventExecutor executor, boolean keepAlive, String id,
        final RequestProcessingPromise requestProcessingPromise) {

        final String reqId = SourceRequestState.instance().getRequestId(channel);
        final ResponseCollector responseCollector = new ResponseCollector();

        final CompletionListener callListener = new CompletionListener(responseCollector,
            ResponseCollector.RESPONSE_A_INDEX,
            requestProcessingPromise) {
            @Override
            protected void onResponseReceived() {
                buildFinalResponseAndFinish(responseCollector, requestProcessingPromise);
            }
        };

        blockingGet(reqId, executor, CALL_A_URI_WITHOUT_ID + id, callListener);
    }

    protected static void buildFinalResponseAndFinish(ResponseCollector responseCollector,
                                                    Promise<FullHttpResponse> requestProcessingPromise) {
        ByteArrayOutputStream outputStream;
        try {

            outputStream = ServiceResponseBuilder.buildTestBResponse(jsonFactory,
                responseCollector.responses[ResponseCollector.RESPONSE_A_INDEX]);
            ByteBuf content = Unpooled.copiedBuffer(outputStream.toByteArray());
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
            requestProcessingPromise.trySuccess(response);
        } catch (IOException e) {
            requestProcessingPromise.tryFailure(new RequestProcessingFailedException(HttpResponseStatus.INTERNAL_SERVER_ERROR, e));
        }
    }
}
