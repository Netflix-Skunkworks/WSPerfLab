package perf.test.netty.server;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.PropertyNames;
import perf.test.netty.server.tests.TestRegistry;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * A simple server based on netty. The server starts on port as specified by {@link PropertyNames#ServerPort}. The netty
 * server pipeline is as defined by {@link ServerPipelineFactory}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class NettyBasedHttpServer {

    private Logger logger = LoggerFactory.getLogger(NettyBasedHttpServer.class);

    private final int port;
    private ServerBootstrap bootstrap;

    public NettyBasedHttpServer() {
        this.port = PropertyNames.ServerPort.getValueAsInt();
    }

    /**
     * This instantiates netty's {@link perf.test.netty.server.ServerBootstrap} and the {@link TestRegistry}
     *
     * @throws InterruptedException If the {@link TestRegistry} was interrupted during startup.
     */
    public void start() throws InterruptedException {
        bootstrap =
                new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        bootstrap.setPipelineFactory(new ServerPipelineFactory());
        bootstrap.bind(new InetSocketAddress(port)) ;
        TestRegistry.init();

        logger.info("Netty server started at port: " + port);
    }

    public void stop() {
        TestRegistry.shutdown();
        bootstrap.releaseExternalResources();
    }
}
