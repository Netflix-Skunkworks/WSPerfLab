package perf.test.netty.server.tests;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry of all {@link TestCaseHandler} implementations.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class TestRegistry {

    private static final Map<String, TestCaseHandler> handlers = new ConcurrentHashMap<String, TestCaseHandler>();

    /**
     * Registers all the handlers, which typically means bootstrapping the client pool.
     *
     * @throws InterruptedException If the client pool (if initialized) in the underlying client could not startup all
     * the connections and was interrupted.
     */
    public static synchronized void init() throws InterruptedException {
        TestCaseA caseA = new TestCaseA();
        handlers.put(caseA.getTestCaseName(), caseA);
    }

    public static synchronized void shutdown() {
        for (TestCaseHandler testCaseHandler : handlers.values()) {
            testCaseHandler.dispose();
        }
    }

    public static TestCaseHandler getHandler(String name) {
        return handlers.get(name);
    }

    public static Collection<TestCaseHandler> getAllHandlers() {
        return handlers.values();
    }
}
