package perf.test.netty.client;

import java.net.InetSocketAddress;

/**
 * @author Nitesh Kant
 */
public class PoolExhaustedException extends Exception {

    private static final long serialVersionUID = 920762909908908354L;

    public PoolExhaustedException(InetSocketAddress serverAddress, int maxConnections) {
        super(String.format("Client pool exhausted for server address %s. Max configured connections: %d", serverAddress, maxConnections));
    }
}
