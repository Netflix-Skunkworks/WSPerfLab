package perf.test.netty.client;

import com.google.common.base.Preconditions;
import javax.annotation.Nullable;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.StatusRetriever;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Nitesh Kant
 */
public class HttpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

    @Nullable
    private final EventExecutor eventExecutor;
    private final EventLoopGroup group;

    private final ConcurrentHashMap<InetSocketAddress, HttpClient<FullHttpResponse, FullHttpRequest>> httpClientsPerServer =
            new ConcurrentHashMap<InetSocketAddress, HttpClient<FullHttpResponse, FullHttpRequest>>();

    private final Map<InetSocketAddress, DedicatedClientPool<FullHttpResponse, FullHttpRequest>> poolsPerServer =
            new HashMap<InetSocketAddress, DedicatedClientPool<FullHttpResponse, FullHttpRequest>>();

    private final AtomicInteger clientHandlerId = new AtomicInteger();

    public HttpClientFactory(@Nullable EventExecutor eventExecutor, EventLoopGroup group) {
        this.eventExecutor = eventExecutor;
        this.group = group;
    }

    public HttpClient<FullHttpResponse, FullHttpRequest> getHttpClient(InetSocketAddress serverAddress)
            throws PoolExhaustedException {
        Preconditions.checkNotNull(serverAddress, "Server address can not be null.");
        HttpClient<FullHttpResponse, FullHttpRequest> client = httpClientsPerServer.get(serverAddress);
        if (null != client) {
            return client;
        }

        DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool;

        synchronized (this) { // Pool creation is strictly once, HttpClient does not hold any state, so it can be created atomically using the CHM
            pool = poolsPerServer.get(serverAddress);
            if (null == pool) {
                Bootstrap bootstrap = newBootstrap();
                try {
                    pool = new DedicatedClientPool<FullHttpResponse, FullHttpRequest>(serverAddress, bootstrap,
                                                                                      PropertyNames.MockBackendMaxConnectionsPerTest.getValueAsInt(),
                                                                                      PropertyNames.MockBackendConnectionsAtStartupPerTest.getValueAsInt());
                    populateChannelInitializer(bootstrap, pool);
                    pool.init();
                    poolsPerServer.put(serverAddress, pool);
                } catch (PoolExhaustedException e) {
                    logger.error("Client pool creation failed.", e);
                    throw e;
                }
            }
        }

        HttpClientImpl newClient = new HttpClientImpl(eventExecutor, pool);
        HttpClient<FullHttpResponse, FullHttpRequest> existingClient = httpClientsPerServer.putIfAbsent(serverAddress, newClient);
        return null != existingClient ? existingClient : newClient;
    }

    public synchronized void shutdown() {
        for (DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool : poolsPerServer.values()) {
            pool.shutdown();
        }
    }

    private Bootstrap newBootstrap() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_KEEPALIVE, true);
        return bootstrap;
    }

    private void populateChannelInitializer(Bootstrap bootstrap,
                                            final DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool) {
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                if (PropertyNames.ClientLoggingEnable.getValueAsBoolean()) {
                    pipeline.addLast("logger", new LoggingHandler(LogLevel.DEBUG));
                }
                pipeline.addLast("codec", new HttpClientCodec());
                pipeline.addLast("aggregator", new HttpObjectAggregator(
                        PropertyNames.ClientChunkSize.getValueAsInt()));
                pipeline.addLast("handler", new ClientHandler(pool, clientHandlerId.incrementAndGet()));
            }
        });
    }

    public void populateStatus(InetSocketAddress mockBackendServerAddress,
                               StatusRetriever.TestCaseStatus testCaseStatus) {
        @Nullable DedicatedClientPool<FullHttpResponse, FullHttpRequest> pool = poolsPerServer.get(mockBackendServerAddress);
        if (null != pool) {
            pool.populateStatus(testCaseStatus);
        }
    }
}
