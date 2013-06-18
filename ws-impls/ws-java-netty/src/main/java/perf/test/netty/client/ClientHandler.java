package perf.test.netty.client;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ClientHandler extends SimpleChannelUpstreamHandler {

    public static final int MAX_RETRIES = 3;
    private final NettyClientPool nettyClientPool;
    private final long id;
    private Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final AtomicInteger retryCount; // This is per connection, which at a time only has one request.

    private final AtomicReference<String> lastCallbackReceived = new AtomicReference<String>();

    /**
     * A handler instance is tied to a particular connection, HTTP can not have concurrent requests on the
     * same connection, so we can maintain this state, making sure that we always call
     * {@link #releaseClientAndSetListener(org.jboss.netty.channel.ChannelHandlerContext)} before doing anything in any
     * callback.
     */
    private volatile NettyClientPool.ClientCompletionListener listener;

    public ClientHandler(NettyClientPool nettyClientPool, long id) {
        this.nettyClientPool = nettyClientPool;
        this.id = id;
        retryCount = new AtomicInteger();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!lastCallbackReceived.compareAndSet(null, "msgReceived")) {
            logger.error("Client id: " + id + ". Multiple calls to the Client handler, current call is msg received, last callback received: " + lastCallbackReceived.get());
        }
        retryCount.set(0); // We got a response, reset retries.
        releaseClientAndSetListener(ctx);
        if (null != listener) {
            listener.onComplete((HttpResponse) e.getMessage());
        } else {
            logger.error("Client id: " + id + "No listener found on message complete, may be coz of an earlier error.");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (!lastCallbackReceived.compareAndSet(null, "error")) {
            logger.error("Client id: " + id + "Multiple calls to the Client handler, current call is error, last callback received: " + lastCallbackReceived.get());
        }
        logger.error("Client id: " + id + "Client handler got an error.", e.getCause());
        releaseClientAndSetListener(ctx);
        if (null != listener) {
            if (!ctx.getChannel().isConnected() && retryCount.incrementAndGet() >= MAX_RETRIES) {
                // Client disconnected, we must retry the request as we only support GET which is idempotent.
                nettyClientPool.retry((NettyClientPool.ClientCompletionListenerWrapper) listener);
            } else {
                listener.onError(e);
            }
        } else {
            logger.error("Client id: " + id + "No listener found on error. Nothing more to do.");
        }
    }

    private void releaseClientAndSetListener(final ChannelHandlerContext ctx) {
        NettyClient client = (NettyClient) ctx.getChannel().getAttachment();
        if (null != client) {
            listener = client.getCurrentRequestCompletionListener();
            //client.release();
        }
    }
}
