package perf.test.netty.server;

import perf.test.netty.ConnectedClientsCounter;
import perf.test.netty.server.tests.TestCaseHandler;
import perf.test.netty.server.tests.TestRegistry;

import java.net.InetSocketAddress;
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
        statusBuilder.append('\n');
        for (Map.Entry<String, TestCaseStatus> statuses : status.getTestNameVSStatus().entrySet()) {
            statusBuilder.append("------------------------------------------------------------------");
            statusBuilder.append('\n');
            statusBuilder.append("Testcase name: ");
            statusBuilder.append(statuses.getKey());
            TestCaseStatus testCaseStatus = statuses.getValue();
            for (Map.Entry<InetSocketAddress, ConnPoolStatus> connPoolStatusEntry : testCaseStatus
                    .getServerVsConnPoolStatus().entrySet()) {
                statusBuilder.append('\n');
                statusBuilder.append("***************************************************************");
                statusBuilder.append('\n');
                statusBuilder.append(String.format("Server host: %s port: %d",
                                                   connPoolStatusEntry.getKey().getHostName(),
                                                   connPoolStatusEntry.getKey().getPort()));
                statusBuilder.append('\n');
                statusBuilder.append("Available Connections count: ");
                statusBuilder.append(connPoolStatusEntry.getValue().getAvailableConnectionsCount());
                statusBuilder.append('\n');
                statusBuilder.append("Total Connections count: ");
                statusBuilder.append(connPoolStatusEntry.getValue().getTotalConnectionsCount());
                statusBuilder.append('\n');
                statusBuilder.append("Unhandled requests since startup: ");
                statusBuilder.append(connPoolStatusEntry.getValue().getUnhandledRequestsSinceStartUp());
                statusBuilder.append('\n');
                statusBuilder.append("***************************************************************");
            }
            statusBuilder.append('\n');
            statusBuilder.append("Inflight tests: ");
            statusBuilder.append(testCaseStatus.getInflightTests());
            statusBuilder.append('\n');
            statusBuilder.append("------------------------------------------------------------------");
            statusBuilder.append('\n');
        }
        return statusBuilder.toString();
    }

    public static class TestCaseStatus {

        private long inflightTests;
        private final Map<InetSocketAddress, ConnPoolStatus> serverVsConnPoolStatus =
                new HashMap<InetSocketAddress, ConnPoolStatus>();

        public void addConnPoolStats(InetSocketAddress server, ConnPoolStatus status) {
            serverVsConnPoolStatus.put(server, status);
        }

        public long getInflightTests() {
            return inflightTests;
        }

        public void setInflightTests(long inflightTests) {
            this.inflightTests = inflightTests;
        }

        public Map<InetSocketAddress, ConnPoolStatus> getServerVsConnPoolStatus() {
            return serverVsConnPoolStatus;
        }
    }

    public static class ConnPoolStatus {

        private long totalConnectionsCount;
        private long availableConnectionsCount;
        private int unhandledRequestsSinceStartUp;

        public long getTotalConnectionsCount() {
            return totalConnectionsCount;
        }

        public void setTotalConnectionsCount(long totalConnectionsCount) {
            this.totalConnectionsCount = totalConnectionsCount;
        }

        public long getAvailableConnectionsCount() {
            return availableConnectionsCount;
        }

        public void setAvailableConnectionsCount(long availableConnectionsCount) {
            this.availableConnectionsCount = availableConnectionsCount;
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
