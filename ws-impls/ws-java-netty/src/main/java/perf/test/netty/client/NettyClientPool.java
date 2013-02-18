package perf.test.netty.client;

import com.google.common.base.Throwables;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyClientPool {

    private Logger logger = LoggerFactory.getLogger(NettyClientPool.class);

    private LinkedBlockingQueue<NettyClient> pool;
    private final String host;
    private final int port;
    private ClientBootstrap bootstrap;
    private ThreadPoolExecutor connectExecutor;
    private volatile boolean shutdown;

    public NettyClientPool(int maxConnections, final int port, final String host) throws InterruptedException {
        this.port = port;
        this.host = host;
        bootstrap();
        pool = new LinkedBlockingQueue<NettyClient>(maxConnections);
        connectExecutor = new ThreadPoolExecutor(Math.min(10, maxConnections), maxConnections, 10, TimeUnit.MINUTES,
                                                 new LinkedBlockingQueue<Runnable>(10));
        List<ChannelFuture> connectFutures = new ArrayList<ChannelFuture>(maxConnections);
        for (int i = 0; i < maxConnections; i++) {
            final NettyClient nettyClient = newClient(new NettyClient.ClientCloseListener() {
                @Override
                public void onClose(NettyClient closedClient) {
                    pool.offer(closedClient);
                }
            });
            try {
                ChannelFuture connect = nettyClient.connect();
                connect.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            logger.info(String.format("A client connected to host %s at port %s", host, port));
                            pool.offer(nettyClient);
                        }
                    }
                });
                connectFutures.add(connect);
            } catch (Throwable throwable) {
                logger.error(String.format("Connect failed for host: %s and port: %s", host, port), throwable);
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
            nettyClient.dispose();
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
            if (!client.isConnected()) {
                // Async connect & use next available.
                connectExecutor.submit(new Callable<ChannelFuture>() {
                    @Override
                    public ChannelFuture call() throws Exception {
                        try {
                            ChannelFuture connect = client.connect();
                            connect.addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        pool.offer(client);
                                    }
                                }
                            });
                            return connect;
                        } catch (Throwable throwable) {
                            logger.error(String.format("Connect failed for host: %s and port: %s", host, port),
                                    throwable);
                            throw Throwables.propagate(throwable);
                        }
                    }
                });
                return getNextAvailableClient();
            }
        }
        return client;
    }

    private NettyClient newClient(NettyClient.ClientCloseListener closeListener) {
        return new NettyClient(bootstrap, closeListener, host, port);
    }

    private void bootstrap() {
        bootstrap =
                new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        bootstrap.setPipelineFactory(new ClientPipelineFactory());

    }
}
