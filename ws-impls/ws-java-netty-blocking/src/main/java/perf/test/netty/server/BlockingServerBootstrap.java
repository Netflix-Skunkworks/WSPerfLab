package perf.test.netty.server;

import perf.test.netty.PropertyNames;

/**
 * @author Nitesh Kant
 */
public class BlockingServerBootstrap extends ServerBootstrap {

    static {
        System.setProperty(PropertyNames.ClientIOBlocking.getPropertyName(), "true");
        System.setProperty(PropertyNames.ServerIOBlocking.getPropertyName(), "true");
        System.setProperty(PropertyNames.ServerContextPath.getPropertyName(), "/ws-java-netty-blocking/");
    }
}
