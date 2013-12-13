package perf.test;

/**
 * All the properties used by netty module. The property values are obtained as {@link System#getProperty(String, String)} for
 * the property name specified by {@link perf.test.PropertyNames#getPropertyName()} with a default value as specified
 * by {@link perf.test.PropertyNames#getDefaultVal()}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public enum PropertyNames {

    ClientConnectTimeout("client.connect.timeout", "1000"),
    ClientSocketTimeout("client.socket.timeout", "1000"),
    ClientConnectionRequestTimeout("client.connection-request.timeout", "1010"),
    ClientMaxConnectionsTotal("client.max-connections-total", "1000"),

    BackendRequestThreadPoolSize("backend-request.max-thread-pool-size", "2000");

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
