package perf.test.rxnetty;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;

public class StartServer {

    public static void main(String[] args) {
        System.out.println("Starting service on port 8888...");
        RxNetty.createHttpServer(8888, (request, response) -> {
            try {
                return TestRouteBasic.handle(request, response);
            } catch (Throwable e) {
                System.err.println("Server => Error [" + request.getPath() + "] => " + e);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                return response.writeStringAndFlush("Error 500: Bad Request\n" + e.getMessage() + "\n");
            }
        }).startAndWait();
    }

}
