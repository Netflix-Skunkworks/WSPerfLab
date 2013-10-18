package perf.test.netty.client;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client abstraction for non-blocking clients. <br/>
 * <b> This client only allows one request processing at any time.</b> <br/>
 *
 * The client is deemed in use till the time, the current request is completed, specifically till the time,
 * {@link ClientHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)}
 * is not called. As soon as this method is invoked (which marks the completion of response reading), the client is
 * released for further use. Any user of this client is free to use it for as many sequential request processing as
 * required. <p/>
 * Since, every request is processed using a new pipeline (created by {@link ClientPipelineFactory} and hence a new
 * {@link ClientHandler}, the state of the request processing, is stored in the handler. <br/>
 * The future associated with a request processing, stores a reference to the handler and hence can get the state of
 * the request processing anytime. <br/>
 * {@link ClientHandler} releases the client for reuse so the user of this client, need not do any explicity client
 * release.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
class NettyClient {

    private Logger logger = LoggerFactory.getLogger(NettyClient.class);

    private Channel channel;
    private final ChannelFutureListener channelCloseListener;
    private NettyClientPool.ClientCompletionListener currentRequestCompletionListener;

    private final ClientBootstrap bootstrap;
    private final AtomicBoolean inUse = new AtomicBoolean();

    NettyClient(ClientBootstrap bootstrap, ChannelFutureListener channelCloseListener) {
        this.bootstrap = bootstrap;
        this.channelCloseListener = channelCloseListener;
    }

    /**
     * Executes an HTTP get request for the passed uri.
     * <b>The host, port & scheme information in the passed URI, if any, is ignored. This information is taken from the
     * associated {@link NettyClientPool}</b>
     * The passed handler is invoked (in the selector thread) after a response is received.
     *
     * @param uri URI for the request.
     *
     * @return The future to retrieve the result.
     */
    void get(URI uri, NettyClientPool.ClientCompletionListener listener) {
        if (!inUse.get()) {
            throw new IllegalStateException("Request issued on a client without claiming it.");
        }
        currentRequestCompletionListener = listener;
        channel.setAttachment(this);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getSchemeSpecificPart());
        channel.write(request);
    }

    ChannelFuture connect() {
        if (isConnected()) {
            return null;
        }
        bootstrap.setOption("child.keepAlive", true);
        ChannelFuture connectFuture = null;
//        ChannelFuture connectFuture = bootstrap.connect(new InetSocketAddress(host, port));
        connectFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    channel = future.getChannel();
                    if (null != channelCloseListener) {
                        channel.getCloseFuture().addListener(channelCloseListener);
                    }
                } else {
//                    logger.error(String.format("Connect failed for host: %s and port: %s", host, port), future.getCause());
                }
            }
        });
        return connectFuture;
    }

    void dispose() {
        if (null != channel) {
            channel.getCloseFuture().awaitUninterruptibly();
        }
    }

    boolean isConnected() {
        return (null != channel && channel.isConnected());
    }

    void release() {
        if (!inUse.compareAndSet(true, false)) {
            throw new IllegalStateException("Attempt to release without claiming the client.");
        } else {
            currentRequestCompletionListener = null;
        }
    }

    boolean claim() {
        return isConnected() && inUse.compareAndSet(false, true);
    }

    NettyClientPool.ClientCompletionListener getCurrentRequestCompletionListener() {
        return currentRequestCompletionListener;
    }
}
