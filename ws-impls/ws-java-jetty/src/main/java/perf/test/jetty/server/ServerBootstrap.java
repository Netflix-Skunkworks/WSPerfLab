package perf.test.jetty.server;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import perf.test.jetty.PropertyNames;

/**
 * @author Nitesh Kant
 */
public class ServerBootstrap {

    public static void main(String[] args) throws Exception {
        Server server = new Server(PropertyNames.ServerPort.getValueAsInt());
        ContextHandler contextHandler = new ContextHandler(PropertyNames.ServerContextPath.getValueAsString());
        AbstractHandler myHandler = new ServerHandler();
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { myHandler});
        contextHandler.setHandler(handlers);
        server.setHandler(handlers);
        server.start();
        server.join();
    }
}
