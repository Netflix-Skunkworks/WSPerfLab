package perf.test.netty.client;

import javax.annotation.Nullable;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import perf.test.netty.server.StatusRetriever;

/**
 * @author Nitesh Kant
 */
public interface HttpClient<T, R extends HttpRequest> {

    Future<T> execute(@Nullable EventExecutor executor, R request, ClientResponseHandler<T> responseHandler)
            throws PoolExhaustedException;

    void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus);

    interface ClientResponseHandler<T> {

        void onComplete(T response);

        void onError(Throwable throwable);
    }
}
