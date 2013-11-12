package perf.test.netty.server;

import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import perf.test.netty.client.PoolExhaustedException;

/**
 * Bootsrap that starts {@link NettyBasedHttpServer}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServerBootstrap {

    public static void main(String[] args) throws InterruptedException, PoolExhaustedException {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());

        final NettyBasedHttpServer nettyBasedHttpServer = new NettyBasedHttpServer();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                nettyBasedHttpServer.stop();
            }
        }));

        nettyBasedHttpServer.start();
    }
}
