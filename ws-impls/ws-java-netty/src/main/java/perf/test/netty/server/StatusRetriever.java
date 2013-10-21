package perf.test.netty.server;

import perf.test.netty.ConnectedClientsCounter;
import perf.test.netty.server.tests.TestCaseHandler;
import perf.test.netty.server.tests.TestRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nitesh Kant
 */
public class StatusRetriever {

    private final ConnectedClientsCounter connectedClientsCounter;

    public StatusRetriever(ConnectedClientsCounter connectedClientsCounter) {
        this.connectedClientsCounter = connectedClientsCounter;
    }

    public String getStatus() {
        Status status = new Status();
        Collection<TestCaseHandler> allHandlers = TestRegistry.getAllHandlers();
        for (TestCaseHandler handler : allHandlers) {
            handler.populateStatus(status);
        }
        StringBuilder statusBuilder = new StringBuilder();
        connectedClientsCounter.populateStatus(status);

        statusBuilder.append("Clients connected on this server port: ");
        statusBuilder.append(status.connectedClients);
        statusBuilder.append("\n");
        for (Map.Entry<String, TestCaseStatus> statuses : status.getTestNameVSStatus().entrySet()) {
            statusBuilder.append("------------------------------------------------------------------");
            statusBuilder.append("\n");
            statusBuilder.append("Testcase name: ");
            statusBuilder.append(statuses.getKey());
            TestCaseStatus testCaseStatus = statuses.getValue();
            statusBuilder.append("\n");
            statusBuilder.append("Available Connections count: ");
            statusBuilder.append(testCaseStatus.getAvailConnectionsCount());
            statusBuilder.append("\n");
            statusBuilder.append("Total Connections count: ");
            statusBuilder.append(testCaseStatus.getAvailConnectionsCount());
            statusBuilder.append("\n");
            statusBuilder.append("Inflight tests: ");
            statusBuilder.append(testCaseStatus.getInflightTests());
            statusBuilder.append("\n");
            statusBuilder.append("Unhandled requests since startup: ");
            statusBuilder.append(testCaseStatus.getUnhandledRequestsSinceStartUp());
            statusBuilder.append("\n");
            statusBuilder.append("------------------------------------------------------------------");
            statusBuilder.append("\n");
        }
        return statusBuilder.toString();
    }

    public static class TestCaseStatus {

        private long availConnectionsCount;
        private long totalConnectionsCount;
        private long inflightTests;
        private int unhandledRequestsSinceStartUp;

        public long getAvailConnectionsCount() {
            return availConnectionsCount;
        }

        public void setAvailConnectionsCount(long connectionsCount) {
            this.availConnectionsCount = connectionsCount;
        }

        public long getTotalConnectionsCount() {
            return totalConnectionsCount;
        }

        public void setTotalConnectionsCount(long connectionsCount) {
            totalConnectionsCount = connectionsCount;
        }

        public long getInflightTests() {
            return inflightTests;
        }

        public void setInflightTests(long inflightTests) {
            this.inflightTests = inflightTests;
        }

        public void setUnhandledRequestsSinceStartUp(int unhandledRequestsSinceStartUp) {
            this.unhandledRequestsSinceStartUp = unhandledRequestsSinceStartUp;
        }

        public int getUnhandledRequestsSinceStartUp() {
            return unhandledRequestsSinceStartUp;
        }
    }

    public static class Status {

        private Map<String, TestCaseStatus> testNameVSStatus = new HashMap<String, TestCaseStatus>();
        private long connectedClients;

        public Map<String, TestCaseStatus> getTestNameVSStatus() {
            return testNameVSStatus;
        }

        public void addTestStatus(String name, TestCaseStatus status) {
            testNameVSStatus.put(name, status);
        }

        public void setConnectedClients(long connectedClients) {
            this.connectedClients = connectedClients;
        }
    }
}
