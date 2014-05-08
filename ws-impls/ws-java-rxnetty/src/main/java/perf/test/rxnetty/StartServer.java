package perf.test.rxnetty;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;

public class StartServer {

    public static void main(String[] args) {
        int port = 8888;
        String backendHost = "localhost";
        int backendPort = 8989;
        if (args.length == 0) {
            // use defaults
        } else if (args.length == 3) {
            port = Integer.parseInt(args[0]);
            backendHost = args[1];
            backendPort = Integer.parseInt(args[2]);
        } else {
            System.err.println("Execute with either no argument (for defaults) or 3 arguments: HOST, BACKEND_HOST, BACKEND_PORT");
            System.exit(-1);
        }

        TestRouteBasic route = new TestRouteBasic(backendHost, backendPort);

        System.out.println("Starting service on port " + port + " with backend at " + backendHost + ":" + backendPort + " ...");
        RxNetty.createHttpServer(port, (request, response) -> {
            try {
                return route.handle(request, response);
            } catch (Throwable e) {
                System.err.println("Server => Error [" + request.getPath() + "] => " + e);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                return response.writeStringAndFlush("Error 500: Bad Request\n" + e.getMessage() + "\n");
            }
        }).startAndWait();
    }

}
