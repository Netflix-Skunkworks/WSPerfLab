package perf.test.netty.client;

import com.google.common.base.Preconditions;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant
 */
public class RoundRobinLB<R extends HttpRequest> implements LoadBalancer<FullHttpRequest> {

    private final InetSocketAddress[] hosts;
    private final AtomicLong nextServerIndex = new AtomicLong();

    public RoundRobinLB(String[] hosts, int port) {
        Preconditions.checkNotNull(hosts, "Hosts can not be null");
        Preconditions.checkArgument(hosts.length >= 0, "Hosts can not be empty.");
        this.hosts = new InetSocketAddress[hosts.length];
        int hostIndex = 0;
        for (String host : hosts) {
            this.hosts[hostIndex++] = new InetSocketAddress(host, port);
        }
    }

    @Override
    public InetSocketAddress nextServer(FullHttpRequest request) {
        int nextIndex = (int) Math.abs(nextServerIndex.getAndIncrement() % hosts.length);
        return hosts[nextIndex];
    }

    @Override
    public InetSocketAddress[] getAllServers() {
        return hosts;
    }
}
