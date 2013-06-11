package perf.test.netty.client;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A pool of {@link NettyClient}s. This is an aggressively initialized client pool, the max connections of which are
 * initialized & connected on startup. The initialization of this pool awaits till all the connection attempts have
 * completed either with an error or success.<br/>
 * A {@link ClientBootstrap} instance is shared across all clients in this pool. <br/>
 * After startup, all new client connections are done in async mode and no method calls to this pool blocks.
 * The pool fails fast on exhaustion of connections since all connect attempts are async. <br/>
 * This pool attempts to bootstrap the connections (upto the max connections) whenever it finds on a connection attempt
 * that there are no valid connections in the pool. This will happen when the backend was down on the last connection
 * attempt. So, in most cases, this pool will be resilient to backend servers which come and go frequently.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyClientPool {

    private Logger logger = LoggerFactory.getLogger(NettyClientPool.class);

    private LinkedBlockingQueue<NettyClient> pool;
    private final String host;
    private int maxConnections;
    private final int port;
    private ClientBootstrap bootstrap;
    private volatile boolean shutdown;
    private final AtomicInteger connectionsInUse = new AtomicInteger();
    private final ReentrantLock poolBootstrapLock = new ReentrantLock();

    public NettyClientPool(int maxConnections, final int port, final String host) throws InterruptedException {
        this.maxConnections = maxConnections;
        this.port = port;
        this.host = host;
        bootstrap();
        pool = new LinkedBlockingQueue<NettyClient>(maxConnections);
        List<ChannelFuture> connectFutures = new ArrayList<ChannelFuture>(maxConnections);
        for (int i = 0; i < maxConnections; i++) {
            ChannelFuture connectFuture = connectNewClientAsync();
            if (null != connectFuture) {
                connectFutures.add(connectFuture);
            }
        }
        for (ChannelFuture connectFuture : connectFutures) {
            connectFuture.awaitUninterruptibly();
        }
    }

    public void shutdown() {
        logger.info("Shutting down client pool.");
        shutdown = true;
        for (NettyClient nettyClient : pool) {
            nettyClient.closeAndForget();
        }
        bootstrap.releaseExternalResources();
    }

    public NettyClient getNextAvailableClient() throws Throwable {
        if (shutdown) {
            logger.warn("Netty client pool is shutting down. No more connections available.");
            return null;
        }
        final NettyClient client = pool.poll();
        if (null != client) {
            if (!client.isConnected() || client.isExpired(System.currentTimeMillis())) {
                // Async connect & use next available.
                connectNewClientAsync();
                return getNextAvailableClient();
            } else {
                connectionsInUse.incrementAndGet();
            }
        } else {
            bootstrapPoolIfNeeded();
        }
        return client;
    }

    private void bootstrapPoolIfNeeded() {
        if (connectionsInUse.get() > 0) {
            return;
        }

        if (poolBootstrapLock.tryLock()) {
            try {
                for (int i = 0; i < maxConnections; i++) {
                    connectNewClientAsync();
                }
            } finally {
                if (poolBootstrapLock.isHeldByCurrentThread()) {
                    poolBootstrapLock.unlock();
                }
            }
        }
    }

    private ChannelFuture connectNewClientAsync() {
        final NettyClient clientToUse = newClient(new ClientStateChangeListenerImpl());
        ChannelFuture connect = null;
        try {
            connect = clientToUse.connect();
            connect.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        logger.info(String.format("A client connected to host %s at port %s", host, port));
                        pool.offer(clientToUse);
                    }
                }
            });
        } catch (Throwable throwable) {
            logger.error(String.format("Connect failed for host: %s and port: %s", host, port), throwable);
        }
        return connect;
    }

    private NettyClient newClient(NettyClient.ClientStateChangeListener stateChangeListener) {
        return new NettyClient(bootstrap, stateChangeListener, host, port);
    }

    private void bootstrap() {
        bootstrap =
                new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        bootstrap.setPipelineFactory(new ClientPipelineFactory());

    }

    private class ClientStateChangeListenerImpl implements NettyClient.ClientStateChangeListener {

        @Override
        public void onClose(NettyClient client) {
            connectionsInUse.decrementAndGet();
            pool.offer(client);
        }
    }
}
