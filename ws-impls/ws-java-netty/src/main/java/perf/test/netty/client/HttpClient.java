package perf.test.netty.client;

import javax.annotation.Nullable;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import perf.test.netty.server.StatusRetriever;

/**
 * @author Nitesh Kant
 */
public interface HttpClient<T, R extends HttpRequest> {

    Future<T> execute(@Nullable EventExecutor executor, R request);

    void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus);

    void populateTrace(StringBuilder traceBuilder);

}
