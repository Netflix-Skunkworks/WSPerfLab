package perf.test.netty.client;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ClientHandler extends SimpleChannelUpstreamHandler {

    public static final int MAX_RETRIES = 3;
    private final NettyClientPool nettyClientPool;
    private final long id;
    private Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final AtomicInteger retryCount; // This is per connection, which at a time only has one request.

    /**
     * A handler instance is tied to a particular connection, HTTP can not have concurrent requests on the
     * same connection, so we can maintain this state, making sure that we always call
     * {@link #extractCompletionListener(org.jboss.netty.channel.ChannelHandlerContext)} before doing anything in any
     * callback.
     */
    private volatile NettyClientPool.ClientCompletionListener listener;
    private volatile NettyClient client;

    public ClientHandler(NettyClientPool nettyClientPool, long id) {
        this.nettyClientPool = nettyClientPool;
        this.id = id;
        retryCount = new AtomicInteger();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        retryCount.set(0); // We got a response, reset retries.
        extractCompletionListener(ctx);
        if (null != listener) {
            listener.onComplete((HttpResponse) e.getMessage());
        } else {
            logger.error("Client id: " + id + ". No listener found on message complete.");
            nettyClientPool.onUnhandledRequest(client);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.error("Client id: " + id + ". Client handler got an error.", e.getCause());
        extractCompletionListener(ctx);
        if (null != listener) {
            int retryCount = this.retryCount.incrementAndGet();
            if (!ctx.getChannel().isConnected() && retryCount <= MAX_RETRIES) {
                NettyClientPool.ClientCompletionListenerWrapper completionListenerWrapper =
                        (NettyClientPool.ClientCompletionListenerWrapper) listener;
                logger.info("Retrying request: " + completionListenerWrapper.getRequestUri() + ", retry count: " + retryCount);
                // Client disconnected, we must retry the request as we only support GET which is idempotent.
                nettyClientPool.retry(client, completionListenerWrapper);
            } else {
                listener.onError(e);
            }
        } else {
            logger.error("Client id: " + id + ". No listener found on error. Nothing more to do.");
            nettyClientPool.onUnhandledRequest(client);
        }
    }

    private void extractCompletionListener(final ChannelHandlerContext ctx) {
        client = (NettyClient) ctx.getChannel().getAttachment();
        if (null != client) {
            listener = client.getCurrentRequestCompletionListener();
            if (null == listener) {
                logger.error("Client id: " + id + ". Got null listener attached to the client.");
            }
        }
    }
}
