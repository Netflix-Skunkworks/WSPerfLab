package perf.test.netty.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import org.codehaus.jackson.JsonFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.NettyUtils;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.tests.TestCaseHandler;
import perf.test.netty.server.tests.TestRegistry;

/**
 * Server handler to server testcase execution requests. This server expects all the requests to start with a valid test
 * case name as specified by {@link TestCaseHandler#getTestCaseName()}, otherwise, this
 * returns a http response with status {@link HttpResponseStatus#NOT_FOUND}. <br/>
 * If the testcase name matches with a handler registered with the {@link TestRegistry}, this server calls the
 * {@link TestCaseHandler#processRequest(Channel, boolean, HttpRequest, QueryStringDecoder)}
 * on that handler. <br/>
 *
 * In case, there is an error in handling an http request, the client connection is not closed. This can be forced to
 * close on every error by setting {@link PropertyNames#ServerCloseConnectionOnError} to <code>true</code>
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private final StatusRetriever statusRetriever;
    private final JsonFactory jsonFactory = new JsonFactory();
    private String contextPath;

    public ServerHandler(StatusRetriever statusRetriever, String contextPath) {
        this.statusRetriever = statusRetriever;
        this.contextPath = contextPath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        QueryStringDecoder qpDecoder = new QueryStringDecoder(request.getUri());
        String path = qpDecoder.path();
        FullHttpResponse response;
        boolean handled = false;

        if (!path.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
            TestCaseHandler handler = TestRegistry.getHandler(path);
            logger.debug(String.format("Test case handler for path %s is %s", path, handler));
            if (null != handler) {
                handler.processRequest(ctx.channel(), keepAlive, request, qpDecoder);
                handled = true;
            } else if (path.startsWith(PropertyNames.StatusRetrieverContextPath.getValueAsString())) {
                System.out.println("Got a status request.");
                String status = statusRetriever.getStatus();
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.content().writeBytes(Unpooled.copiedBuffer(status.getBytes()));
                NettyUtils.sendResponse(ctx.channel(), keepAlive, jsonFactory, response);
                handled = true;
            }
        }

        if (!handled) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            NettyUtils.sendResponse(ctx.channel(), keepAlive, jsonFactory, response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        if (cause instanceof Error) {
            logger.error("Error during server handling, No more processing can be done.", cause);
            return;
        }

        if (!ctx.channel().isActive()) {
            logger.error("Server handler received error, when the channel was closed. Nothing much can be done now.", cause);
            return;
        }

        logger.error("Server handler received error. Will send an error response.", cause);

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);

        NettyUtils.createErrorResponse(jsonFactory, response, cause.getMessage());

        // Add 'Content-Length' header only for a keep-alive connection.
        response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, response.content().readableBytes());
        // Add keep alive header as per:
        // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
        response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

        ChannelFuture writeFuture = ctx.channel().writeAndFlush(response);

        if (PropertyNames.ServerCloseConnectionOnError.getValueAsBoolean()) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
