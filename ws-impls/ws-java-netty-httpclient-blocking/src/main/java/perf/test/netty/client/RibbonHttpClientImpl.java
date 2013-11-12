package perf.test.netty.client;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import perf.test.netty.server.StatusRetriever.TestCaseStatus;

import javax.annotation.Nullable;

/**
 * @author mhawthorne
 */
public class RibbonHttpClientImpl implements HttpClient<FullHttpResponse, FullHttpRequest> {

    @Override
    public Future<FullHttpResponse> execute(@Nullable EventExecutor executor, FullHttpRequest request) {
        boolean b = true;
        throw new UnsupportedOperationException();
    }

    @Override
    public void populateStatus(TestCaseStatus testCaseStatus) {
        boolean b = true;
    }

    @Override
    public void populateTrace(StringBuilder traceBuilder) {
        boolean b = true;
    }

}
