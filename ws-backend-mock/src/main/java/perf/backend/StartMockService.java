package perf.backend;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;

import java.util.List;

import rx.Observable;

public class StartMockService {

    public static void main(String[] args) {
        System.out.println("Starting mock service on port 8080...");
        RxNetty.createHttpServer(8080, (request, response) -> {
            try {
                return handleRequest(request, response);
            } catch (Throwable e) {
                e.printStackTrace();
                System.err.println("Server => Error [" + request.getPath() + "] => " + e);
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                return response.writeStringAndFlush("Error 500: Bad Request\n" + e.getMessage() + "\n");
            }
        }).startAndWait();
    }

    protected static Observable<Void> writeError(HttpServerRequest<?> request, HttpServerResponse<?> response, String message) {
        System.err.println("Server => Error [" + request.getPath() + "] => " + message);
        response.setStatus(HttpResponseStatus.BAD_REQUEST);
        return response.writeStringAndFlush("Error 500: " + message + "\n");
    }

    protected static int getParameter(HttpServerRequest<?> request, String key, int defaultValue) {
        List<String> v = request.getQueryParameters().get(key);
        if (v == null || v.size() != 1) {
            return defaultValue;
        } else {
            return Integer.parseInt(String.valueOf(v.get(0)));
        }
    }

    private static Observable<Void> handleRequest(HttpServerRequest<?> request, HttpServerResponse<?> response) {
        List<String> _id = request.getQueryParameters().get("id");
        if (_id == null || _id.size() != 1) {
            return writeError(request, response, "Please provide a numerical 'id' value. It can be a random number (uuid). Received => " + _id);
        }
        long id = Long.parseLong(String.valueOf(_id.get(0)));

        int delay = getParameter(request, "delay", 50); // default to 50ms server-side delay
        int itemSize = getParameter(request, "itemSize", 128); // default to 128 bytes item size (assuming ascii text)
        int numItems = getParameter(request, "numItems", 10); // default to 10 items in a list

        // no more than 100 items
        if (numItems < 1 || numItems > 100) {
            return writeError(request, response, "Please choose a 'numItems' value from 1 to 100.");
        }

        // no larger than 50KB per item
        if (itemSize < 1 || itemSize > 1024 * 50) {
            return writeError(request, response, "Please choose an 'itemSize' value from 1 to 1024*50 (50KB).");
        }

        // no larger than 60 second delay
        if (delay < 0 || delay > 60000) {
            return writeError(request, response, "Please choose a 'delay' value from 0 to 60000 (60 seconds).");
        }

        response.setStatus(HttpResponseStatus.OK);
        return MockResponse.generateJson(id, delay, itemSize, numItems).flatMap(json -> response.writeStringAndFlush(json));
    }

}
