package perf.test.rxnetty;

import io.reactivex.netty.metrics.HttpClientMetricEventsListener;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Nitesh Kant
 */
public class ConnectionPoolMetricListener extends HttpClientMetricEventsListener {

    private LongAdder inUseCount = new LongAdder();
    private LongAdder idleCount = new LongAdder();
    private LongAdder totalConnections = new LongAdder();
    private LongAdder pendingAcquire = new LongAdder();
    private LongAdder pendingRelease = new LongAdder();

    @Override
    protected void onPoolReleaseFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        pendingRelease.decrement();
    }

    @Override
    protected void onPoolReleaseSuccess(long duration, TimeUnit timeUnit) {
        pendingRelease.decrement();
        idleCount.increment();
        inUseCount.decrement();
    }

    @Override
    protected void onPooledConnectionReuse(long duration, TimeUnit timeUnit) {
        idleCount.decrement();
    }

    @Override
    protected void onPoolReleaseStart() {
        pendingRelease.increment();
    }

    @Override
    protected void onPoolAcquireStart() {
        pendingAcquire.increment();
    }

    @Override
    protected void onPoolAcquireFailed(long duration, TimeUnit timeUnit, Throwable throwable) {
        pendingAcquire.decrement();
    }

    @Override
    protected void onPoolAcquireSuccess(long duration, TimeUnit timeUnit) {
        pendingAcquire.decrement();
        inUseCount.increment();
    }

    @Override
    protected void onConnectionCloseSuccess(long duration, TimeUnit timeUnit) {
        totalConnections.decrement();
    }

    @Override
    protected void onConnectSuccess(long duration, TimeUnit timeUnit) {
        totalConnections.increment();
    }

    public long getInUseCount() {
        return inUseCount.longValue();
    }

    public long getIdleCount() {
        return idleCount.longValue();
    }

    public long getTotalConnections() {
        return totalConnections.longValue();
    }

    public long getPendingAcquire() {
        return pendingAcquire.longValue();
    }

    public long getPendingRelease() {
        return pendingRelease.longValue();
    }
}
