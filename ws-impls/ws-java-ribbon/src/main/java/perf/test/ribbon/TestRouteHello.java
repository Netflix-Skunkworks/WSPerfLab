package perf.test.ribbon;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class TestRouteHello {

    private static final byte[] MSG = "Hello World".getBytes();
    public static final int HELLO_WORLD_LENGTH = MSG.length;
    public static final String HELLO_WORLD_LENGTH_STR = String.valueOf(MSG.length);

    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        response.getHeaders().set(HttpHeaders.Names.CONTENT_LENGTH, HELLO_WORLD_LENGTH_STR);
        response.write(response.getAllocator().buffer(HELLO_WORLD_LENGTH).writeBytes(MSG));
        return response.close();
    }
}
