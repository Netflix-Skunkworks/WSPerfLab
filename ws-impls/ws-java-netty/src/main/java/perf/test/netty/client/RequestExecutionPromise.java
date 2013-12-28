package perf.test.netty.client;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

/**
* @author Nitesh Kant
*/
public interface RequestExecutionPromise<S> extends Promise<S> {

    /**
     * Only available when the promise is done.
     *
     * @return EventExecutor on which the request was executed.
     *
     * @throws IllegalStateException If this is called before the promise is done.
     */
    EventExecutor getExecutingClientExecutor();
}
