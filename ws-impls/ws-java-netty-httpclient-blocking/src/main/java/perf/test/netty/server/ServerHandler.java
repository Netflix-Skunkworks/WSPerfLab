package perf.test.netty.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.EventLogger;
import perf.test.netty.PerformanceLogger;
import perf.test.netty.PropertyNames;
import perf.test.netty.SourceRequestState;
import perf.test.netty.server.tests.TestCaseHandler;
import perf.test.netty.server.tests.TestRegistry;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server handler to server testcase execution requests. This server expects all the requests to start with a valid test
 * case name as specified by {@link TestCaseHandler#getTestCaseName()}, otherwise, this
 * returns a http response with status {@link HttpResponseStatus#NOT_FOUND}. <br/>
 *
 * In case, there is an error in handling an http request, the client connection is not closed. This can be forced to
 * close on every error by setting {@link PropertyNames#ServerCloseConnectionOnError} to {@code true}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private static final ConcurrentHashMap<Integer, AtomicLong> respCodeVsCount = new ConcurrentHashMap<Integer, AtomicLong>(); //TODO: Should be test specific.
    private static final AtomicLong sendFailedCount = new AtomicLong(); //TODO: Should be test specific.

    private final StatusRetriever statusRetriever;
    private final JsonFactory jsonFactory = new JsonFactory();
    private final String contextPath;

    private static final AttributeKey<Promise<FullHttpResponse>> promiseKey =
            new AttributeKey<Promise<FullHttpResponse>>("req_processing_complete_promise");

    private static final AttributeKey<Boolean> testCaseRequest = new AttributeKey<Boolean>("is_test_case_request");
    private static final AttributeKey<String> testCaseName = new AttributeKey<String>("test_case_name");

    private static final ConcurrentLinkedQueue<RequestProcessingPromise> allPromises = new ConcurrentLinkedQueue<RequestProcessingPromise>();

    public ServerHandler(StatusRetriever statusRetriever, String contextPath) {
        this.statusRetriever = statusRetriever;
        this.contextPath = contextPath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        final RequestProcessingPromise requestProcessingPromise = new RequestProcessingPromise(ctx.executor());

        requestProcessingPromise.addListener(new RequestProcessingCompleteListener(ctx));
        ctx.channel().attr(promiseKey).set(requestProcessingPromise);
        ctx.channel().attr(testCaseRequest).set(false);

        final String reqId = SourceRequestState.instance().getRequestId(ctx.channel());

        EventLogger.log(reqId, "request-start");

        final PerformanceLogger perfLogger = PerformanceLogger.instance();
        perfLogger.start(reqId, "top");

        QueryStringDecoder qpDecoder = new QueryStringDecoder(request.getUri());
        String path = qpDecoder.path();
        boolean handled = false;

        if (!path.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
            TestCaseHandler handler = TestRegistry.getHandler(path);
//            logger.debug(String.format("Test case handler for path %s is %s", path, handler));
            if (null != handler) {
                ctx.channel().attr(testCaseName).set(path);
                ctx.channel().attr(testCaseRequest).set(true);
                if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                    allPromises.add(requestProcessingPromise);
                }
                handler.processRequest(ctx.channel(), ctx.executor(), request, qpDecoder, requestProcessingPromise);
                handled = true;
            } else if (path.startsWith(PropertyNames.StatusRetrieverContextPath.getValueAsString())) {
                ctx.channel().attr(testCaseRequest).set(false);
                String status = statusRetriever.getStatus(new StatusRetriever.Status());
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.content().writeBytes(Unpooled.copiedBuffer(status.getBytes()));
                requestProcessingPromise.setSuccess(response);
                handled = true;
            } else if (path.startsWith(PropertyNames.RequestTracerContentPath.getValueAsString())) {
                ctx.channel().attr(testCaseRequest).set(false);
                StringBuilder traceBuilder = new StringBuilder();
                if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                    traceBuilder.append("############## Inbound ###################");
                    for (RequestProcessingPromise aPromise : allPromises) {
                        aPromise.populateTrace(traceBuilder);
                    }
                    traceBuilder.append('\n');
                    traceBuilder.append("############## Outbound ###################");
                    Collection<TestCaseHandler> allHandlers = TestRegistry.getAllHandlers();
                    for (TestCaseHandler aHandler : allHandlers) {
                        aHandler.populateTrace(traceBuilder);
                    }
                    traceBuilder.append('\n');
                } else {
                    traceBuilder.append("Tracing is not enabled, enable it by setting the property: ")
                                .append(PropertyNames.ServerTraceRequests.name())
                                .append(" to true");
                }
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.content().writeBytes(Unpooled.copiedBuffer(traceBuilder.toString().getBytes()));
                requestProcessingPromise.setSuccess(response);
                handled = true;
            }
        }

        if (!handled) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            requestProcessingPromise.setSuccess(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        if (!ctx.channel().isActive()) {
            logger.error("Server handler received error, when the channel was closed. Nothing much can be done now.", cause);
            return;
        }

        logger.error("Server handler received error. Will send an error response.", cause);
        Promise<FullHttpResponse> promise = ctx.channel().attr(promiseKey).get();
        if (null != promise) {
            if (!promise.tryFailure(cause)) {
                logger.info("Failed to set failure on the request completion promise as it is already finished.", cause);
            }
        } else {
            logger.error("No promise found on server handler on error. Nothing can be done now.");
        }
    }

    public static void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus) {
        testCaseStatus.setRespCodeVsCount(respCodeVsCount);
        testCaseStatus.setSendFailedCount(sendFailedCount.get());
    }

    private class RequestProcessingCompleteListener implements GenericFutureListener<RequestProcessingPromise> {

        private final ChannelHandlerContext channelHandlerContext;

        private RequestProcessingCompleteListener(ChannelHandlerContext channelHandlerContext) {
            this.channelHandlerContext = channelHandlerContext;
        }

        @Override
        public void operationComplete(RequestProcessingPromise promise) throws Exception {
            if (PropertyNames.ServerTraceRequests.getValueAsBoolean()) {
                allPromises.remove(promise);
            }

            final FullHttpResponse response;
            final boolean isTestCaseRequest = channelHandlerContext.channel().attr(testCaseRequest).get();

            if (promise.isSuccess()) {
                response = promise.get();
            } else {
                HttpResponseStatus responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                @Nullable Throwable failure = promise.cause();
                logger.error("Test request processing failed, sending an error response.", failure);
                if (failure instanceof RequestProcessingFailedException) {
                    responseStatus = ((RequestProcessingFailedException) failure).getStatus();
                }

                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                JsonGenerator jsonGenerator;
                try {
                    jsonGenerator = jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("Error", failure != null ? failure.getMessage() : "Unknown");
                    jsonGenerator.writeEndObject();
                    jsonGenerator.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                response.setStatus(responseStatus);
                response.content().writeBytes(Unpooled.copiedBuffer(out.toByteArray()));
                response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
            }

            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

            final String reqId = SourceRequestState.instance().getRequestId(channelHandlerContext.channel());
            EventLogger.log(reqId, "response-generated");

            ChannelFuture writeFuture =
                    channelHandlerContext.channel().writeAndFlush(response).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            EventLogger.log(reqId, "response-flush-end");

                            if (isTestCaseRequest) {
                                if (future.isSuccess()) {
                                    AtomicLong respCodeCount = new AtomicLong();
                                    AtomicLong existing = respCodeVsCount.putIfAbsent(response.getStatus().code(),
                                                                                      respCodeCount);
                                    if (null != existing) {
                                        respCodeCount = existing;
                                    }
                                    respCodeCount.incrementAndGet();
                                } else {
                                    logger.error("Failed to send response back to the client.", future.cause());
                                    sendFailedCount.incrementAndGet();
                                }
                            }

                            PerformanceLogger.instance().stop(reqId, "top");
                            EventLogger.log(reqId, "request-end");
                        }
                    });

            EventLogger.log(reqId, "response-flush-start");

            if (!promise.isSuccess() && PropertyNames.ServerCloseConnectionOnError.getValueAsBoolean()) {
                writeFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
