package perf.test.netty;

/**
 * All the properties used by netty module. The property values are obtained as {@link System#getProperty(String, String)} for
 * the property name specified by {@link perf.test.netty.PropertyNames#getPropertyName()} with a default value as specified
 * by {@link perf.test.netty.PropertyNames#getDefaultVal()}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public enum PropertyNames {

    ServerContextPath("http.server.context.path", "/ws-java-netty/"),
    ServerPort("http.server.port", "8798"),
    ServerLoggingEnable("server.log.enable", "false"),
    ServerChunkSize("server.chunk.size", "1048576"),
    ServerCloseConnectionOnError("server.close.conn.on.error", "false"), // Since we always serve HTTP - 1.1., we assume its keep alive.

    ClientLoggingEnable("client.log.enable", "false"),
    ClientChunkSize("client.chunk.size", "1048576"),
    ClientReadTimeout("client.read.timeout", "500"),

    MockBackendHost("perf.test.backend.host", "localhost"),
    MockBackendPort("perf.test.backend.port", "8989"),
    MaxBackendContextPath("perf.test.backend.context.path", "/ws-backend-mock"),
    MockBackendMaxConnectionsPerTest("perf.test.backend.host.maxconn.per.test", "100");


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
