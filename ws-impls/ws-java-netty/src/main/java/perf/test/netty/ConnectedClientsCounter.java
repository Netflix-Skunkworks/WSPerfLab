package perf.test.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perf.test.netty.server.StatusRetriever;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant (nkant@netflix.com)
 */
@ChannelHandler.Sharable
public class ConnectedClientsCounter extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ConnectedClientsCounter.class);

    private final AtomicLong connectedClients = new AtomicLong();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isInfoEnabled()) {
            Channel parent = ctx.channel().parent();
            SocketAddress serverAddress = parent.localAddress();
            int serverPort = 0;
            InetSocketAddress remoteAddr = null;
            if (serverAddress instanceof InetSocketAddress) {
                serverPort = ((InetSocketAddress) serverAddress).getPort();
            }

            SocketAddress clientAddr = ctx.channel().remoteAddress();
            if (clientAddr instanceof InetSocketAddress) {
                remoteAddr = (InetSocketAddress) clientAddr;
            }
            logger.info("New client connected on server port {} and remote host {}, port {}", serverPort,
                        null != remoteAddr ? remoteAddr.getHostName() : "Unknown",
                        null != remoteAddr ? remoteAddr.getPort() : "Unknown");
        }
        connectedClients.incrementAndGet();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isInfoEnabled()) {
            Channel parent = ctx.channel().parent();
            SocketAddress serverAddress = parent.localAddress();
            int serverPort = 0;
            InetSocketAddress remoteAddr = null;
            if (serverAddress instanceof InetSocketAddress) {
                serverPort = ((InetSocketAddress) serverAddress).getPort();
            }

            SocketAddress clientAddr = ctx.channel().remoteAddress();
            if (clientAddr instanceof InetSocketAddress) {
                remoteAddr = (InetSocketAddress) clientAddr;
            }
            logger.info("Client disconnected from server port {} and remote host {}, port {}", serverPort,
                        null != remoteAddr ? remoteAddr.getHostName() : "Unknown",
                        null != remoteAddr ? remoteAddr.getPort() : "Unknown");
        }
        connectedClients.decrementAndGet();
        super.channelInactive(ctx);
    }

    public void populateStatus(StatusRetriever.Status status) {
        status.setConnectedClients(connectedClients.get());
    }
}
