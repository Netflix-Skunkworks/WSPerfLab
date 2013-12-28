package perf.test.netty;

/**
 * All the properties used by netty module. The property values are obtained as {@link System#getProperty(String, String)} for
 * the property name specified by {@link perf.test.netty.PropertyNames#getPropertyName()} with a default value as specified
 * by {@link perf.test.netty.PropertyNames#getDefaultVal()}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public enum PropertyNames {

    ServerIOBlocking("http.server.io.blocking", "false"),
    ServerEventLoopCount("http.server.eventloop.count", "0"), // 0 means default, should be set if io is blocking
    ServerContextPath("http.server.context.path", "/ws-java-netty/"),
    ServerPort("http.server.port", "8798"),
    ServerLoggingEnable("server.log.enable", "true"),
    ServerChunkSize("server.chunk.size", "1048576"),
    ServerTraceRequests("server.trace.requests", "false"),
    ServerCloseConnectionOnError("server.close.conn.on.error", "false"), // Since we always serve HTTP - 1.1., we assume its keep alive.

    ClientIOBlocking("client.io.blocking", "false"),
    ClientEventLoopCount("http.client.eventloop.count", "0"), // 0 means default, should be set if io is blocking
    ClientLoggingEnable("client.log.enable", "true"),
    ClientChunkSize("client.chunk.size", "1048576"),
    ClientReadTimeOutSeconds("client.read.timeout.seconds", "30"),

    MockBackendHost("perf.test.backend.host", "localhost"),
    MockBackendPort("perf.test.backend.port", "8989"),
    MockBackendContextPath("perf.test.backend.context.path", "/ws-backend-mock"),
    MockBackendConnectionsAtStartupPerTest("perf.test.backend.host.startupconn.per.test", "10"),
    MockBackendMaxConnectionsPerTest("perf.test.backend.host.maxconn.per.test", "10"),

    StatusRetrieverContextPath("http.server.status.endpoint", "status/"),
    RequestTracerContentPath("http.server.tracer.endpoint", "trace/"),

    TestCaseACallANumItems("perf.test.testA.callA.numItems", "2"),
    TestCaseACallAItemSize("perf.test.testA.callA.itemSize", "50"),
    TestCaseACallAItemDelay("perf.test.testA.callA.delay", "50"),

    TestCaseACallBNumItems("perf.test.testA.callB.numItems", "25"),
    TestCaseACallBItemSize("perf.test.testA.callB.itemSize", "30"),
    TestCaseACallBItemDelay("perf.test.testA.callB.delay", "150"),

    TestCaseACallCNumItems("perf.test.testA.callC.numItems", "1"),
    TestCaseACallCItemSize("perf.test.testA.callC.itemSize", "5000"),
    TestCaseACallCItemDelay("perf.test.testA.callC.delay", "80"),

    TestCaseACallDNumItems("perf.test.testA.callD.numItems", "1"),
    TestCaseACallDItemSize("perf.test.testA.callD.itemSize", "1000"),
    TestCaseACallDItemDelay("perf.test.testA.callD.delay", "1"),

    TestCaseACallENumItems("perf.test.testA.callE.numItems", "100"),
    TestCaseACallEItemSize("perf.test.testA.callE.itemSize", "30"),
    TestCaseACallEItemDelay("perf.test.testA.callE.delay", "40");
    
    private String propertyName;
    private String defaultVal;

    PropertyNames(String propertyName, String defaultVal) {
        this.propertyName = propertyName;
        this.defaultVal = defaultVal;
    }

    public String getDefaultVal() {
        return defaultVal;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public boolean getValueAsBoolean() {
        return Boolean.getBoolean(propertyName);
    }

    public int getValueAsInt() {
        String property = System.getProperty(propertyName, defaultVal);
        return Integer.parseInt(property);
    }

    public String getValueAsString() {
        return System.getProperty(propertyName, defaultVal);
    }
}
