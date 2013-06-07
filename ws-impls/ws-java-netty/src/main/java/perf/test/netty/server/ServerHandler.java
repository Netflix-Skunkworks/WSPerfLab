package perf.test.netty.server;

import org.codehaus.jackson.JsonFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.NettyUtils;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.tests.TestCaseHandler;
import perf.test.netty.server.tests.TestRegistry;

/**
 * Server handler to server testcase execution requests. This server expects all the requests to start with a valid test
 * case name as specified by {@link perf.test.netty.server.tests.TestCaseHandler#getTestCaseName()}, otherwise, this
 * returns a http response with status {@link HttpResponseStatus#NOT_FOUND}. <br/>
 * If the testcase name matches with a handler registered with the {@link TestRegistry}, this server calls the
 * {@link TestCaseHandler#processRequest(org.jboss.netty.channel.Channel, boolean, org.jboss.netty.handler.codec.http.HttpRequest, org.jboss.netty.handler.codec.http.QueryStringDecoder)}
 * on that handler. <br/>
 *
 * In case, there is an error in handling an http request, the client connection is not closed. This can be forced to
 * close on every error by setting {@link PropertyNames#ServerCloseConnectionOnError} to <code>true</code>
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServerHandler extends SimpleChannelUpstreamHandler {

    private Logger logger = LoggerFactory.getLogger(ServerHandler.class);

    private final JsonFactory jsonFactory = new JsonFactory();
    private String contextPath;

    public ServerHandler(String contextPath) {
        this.contextPath = contextPath;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        QueryStringDecoder qpDecoder = new QueryStringDecoder(request.getUri());
        String path = qpDecoder.getPath();
        HttpResponse response;
        if (!path.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
            TestCaseHandler handler = TestRegistry.getHandler(path);
            logger.debug(String.format("Test case handler for path %s is %s", path, handler));
            if (null != handler) {
                handler.processRequest(e.getChannel(), keepAlive, request, qpDecoder);
            }
        } else {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            NettyUtils.sendResponse(e.getChannel(), keepAlive, jsonFactory, response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Throwable cause = e.getCause();

        if (cause instanceof Error) {
            logger.error("Error during server handling, No more processing can be done.", cause);
            return;
        }

        if (!ctx.getChannel().isConnected()) {
            logger.error("Server handler received error, when the channel was closed. Nothing much can be done now.", cause);
            return;
        } else {
            logger.error("Server handler received error. Will send an error response.", cause);
        }

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);

        NettyUtils.createErrorResponse(jsonFactory, response, cause.getMessage());

        // Add 'Content-Length' header only for a keep-alive connection.
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent().readableBytes());
        // Add keep alive header as per:
        // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
        response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

        ChannelFuture writeFuture = e.getChannel().write(response);

        if (PropertyNames.ServerCloseConnectionOnError.getValueAsBoolean()) {
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
