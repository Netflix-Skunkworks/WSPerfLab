package perf.test.jetty.server.tests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry of all {@link TestCaseHandler} implementations.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public class TestRegistry {

    private static Logger logger = LoggerFactory.getLogger(TestRegistry.class);

    private static final Map<String, TestCaseHandler> handlers = new ConcurrentHashMap<String, TestCaseHandler>();

    /**
     * Registers all the handlers.
     */
    public static synchronized void init() throws Exception {
        TestCaseA caseA = new TestCaseA();
        handlers.put(caseA.getTestCaseName(), caseA);
    }

    public static synchronized void shutdown() {
        for (TestCaseHandler testCaseHandler : handlers.values()) {
            try {
                testCaseHandler.dispose();
            } catch (Exception e) {
                logger.error("Failed to dispose a handler.", e);
            }
        }
    }

    public static TestCaseHandler getHandler(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return handlers.get(name);
    }
}
