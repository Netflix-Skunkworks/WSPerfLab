package perf.test.netty.client;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;

import java.net.InetSocketAddress;

/**
 * @author Nitesh Kant
 */
public class LBAwareHttpClientImpl extends LBAwareHttpClient<FullHttpResponse, FullHttpRequest> {

    public LBAwareHttpClientImpl(LoadBalancer<FullHttpRequest> loadBalancer,
                                 HttpClientFactory clientFactory) {
        super(loadBalancer, clientFactory);
    }

    @Override
    protected HttpClient<FullHttpResponse, FullHttpRequest> getClient(HttpClientFactory clientFactory,
                                                                      InetSocketAddress nextServer)
            throws PoolExhaustedException {
        return clientFactory.getHttpClient(nextServer);
    }
}
