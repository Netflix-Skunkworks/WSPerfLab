package perf.test.netty.client;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import perf.test.netty.server.StatusRetriever;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

/**
 * @author Nitesh Kant
 */
public abstract class LBAwareHttpClient<T, R extends HttpRequest> implements HttpClient<T, R> {

    private final LoadBalancer<R> loadBalancer;
    private final HttpClientFactory clientFactory;

    protected LBAwareHttpClient(LoadBalancer<R> loadBalancer, HttpClientFactory clientFactory) {
        this.loadBalancer = loadBalancer;
        this.clientFactory = clientFactory;
    }

    @Override
    public Future<T> execute(@Nullable EventExecutor executor, R request,
                             ClientResponseHandler<T> responseHandler) throws PoolExhaustedException {
        InetSocketAddress nextServer = loadBalancer.nextServer(request);
        HttpClient<T, R> httpClient = getClient(clientFactory, nextServer);
        return httpClient.execute(executor, request, responseHandler);
    }

    @Override
    public void populateStatus(StatusRetriever.TestCaseStatus testCaseStatus) {
        for (InetSocketAddress serverAddr : loadBalancer.getAllServers()) {
            clientFactory.populateStatus(serverAddr, testCaseStatus);
        }
    }

    protected abstract HttpClient<T, R> getClient(HttpClientFactory clientFactory, InetSocketAddress nextServer)
            throws PoolExhaustedException;
}
