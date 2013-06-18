package perf.test.netty.server;

import perf.test.netty.server.tests.TestCaseHandler;
import perf.test.netty.server.tests.TestRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nitesh Kant
 */
public class StatusRetriever {

    public static String getStatus() {
        Status status = new Status();
        Collection<TestCaseHandler> allHandlers = TestRegistry.getAllHandlers();
        for (TestCaseHandler handler : allHandlers) {
            handler.populateStatus(status);
        }
        StringBuilder statusBuilder = new StringBuilder();
        for (Map.Entry<String, TestCaseStatus> statuses : status.getTestNameVSStatus().entrySet()) {
            statusBuilder.append("------------------------------------------------------------------");
            statusBuilder.append("\n");
            statusBuilder.append("Testcase name: ");
            statusBuilder.append(statuses.getKey());
            TestCaseStatus testCaseStatus = statuses.getValue();
            statusBuilder.append("\n");
            statusBuilder.append("Request Queue Size: ");
            statusBuilder.append(testCaseStatus.getRequestQueueSize());
            statusBuilder.append("\n");
            statusBuilder.append("Connection count: ");
            statusBuilder.append(testCaseStatus.getConnectionsCount());
            statusBuilder.append("\n");
            statusBuilder.append("Inflight tests: ");
            statusBuilder.append(testCaseStatus.getInflightTests());
            statusBuilder.append("\n");
            statusBuilder.append("------------------------------------------------------------------");
            statusBuilder.append("\n");
        }
        return statusBuilder.toString();
    }

    public static class TestCaseStatus {

        private long requestQueueSize;
        private long connectionsCount;
        private long inflightTests;

        public long getRequestQueueSize() {
            return requestQueueSize;
        }

        public void setRequestQueueSize(long requestQueueSize) {
            this.requestQueueSize = requestQueueSize;
        }

        public long getConnectionsCount() {
            return connectionsCount;
        }

        public void setConnectionsCount(long connectionsCount) {
            this.connectionsCount = connectionsCount;
        }

        public long getInflightTests() {
            return inflightTests;
        }

        public void setInflightTests(long inflightTests) {
            this.inflightTests = inflightTests;
        }
    }

    public static class Status {

        private Map<String, TestCaseStatus> testNameVSStatus = new HashMap<>();

        public Map<String, TestCaseStatus> getTestNameVSStatus() {
            return testNameVSStatus;
        }

        public void addTestStatus(String name, TestCaseStatus status) {
            testNameVSStatus.put(name, status);
        }
    }
}
