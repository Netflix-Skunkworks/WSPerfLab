package perf.test.netty.client;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ClientHandler extends SimpleChannelUpstreamHandler {

    private Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    /**
     * A handler instance is tied to a particular connection, HTTP can not have concurrent requests on the
     * same connection, so we can maintain this state, making sure that we always call
     * {@link #releaseClientAndSetListener(org.jboss.netty.channel.ChannelHandlerContext)} before doing anything in any
     * callback.
     */
    private volatile NettyClient.ClientCompletionListener listener;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        releaseClientAndSetListener(ctx);
        if (null != listener) {
            listener.onComplete((HttpResponse) e.getMessage());
        } else {
            logger.error("No listener found on message complete, may be coz of an earlier error.");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.error("Client handler got an error.", e);
        releaseClientAndSetListener(ctx);
        listener.onError(e);
    }

    private void releaseClientAndSetListener(final ChannelHandlerContext ctx) {
        NettyClient client = (NettyClient) ctx.getChannel().getAttachment();
        listener = client.getCurrentRequestCompletionListener();
        client.release(); // Release the client to be reused.
    }
}
