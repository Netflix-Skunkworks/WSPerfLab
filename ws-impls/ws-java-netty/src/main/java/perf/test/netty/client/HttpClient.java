package perf.test.netty.client;

import com.sun.istack.internal.Nullable;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;

/**
 * @author Nitesh Kant
 */
public interface HttpClient<T, R extends HttpRequest> {

    Future<T> execute(@Nullable EventExecutor executor, R request, ClientResponseHandler<T> responseHandler);

    interface ClientResponseHandler<T> {

        void onComplete(T response);

        void onError(Throwable throwable);
    }
}
