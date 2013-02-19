package perf.test.netty.server;

import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;

/**
 * Bootsrap that starts {@link NettyBasedHttpServer}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class ServerBootstrap {

    public static void main(String[] args) throws InterruptedException {
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
