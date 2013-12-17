package perf.test.utils;

/**
 * All the properties used by netty module. The property values are obtained as {@link System#getProperty(String, String)} for
 * the property name specified by {@link perf.test.netty.PropertyNames#getPropertyName()} with a default value as specified
 * by {@link perf.test.netty.PropertyNames#getDefaultVal()}
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
public enum PropertyNames {

    EventLogSamplePercentage("event-log.sample-percentage", "100"),
    PerfLogEnabled("perf-log.enabled", "false");

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
        String property = getValueAsString();
        return Boolean.getBoolean(property);
    }

    public int getValueAsInt() {
        String property = getValueAsString();
        return Integer.parseInt(property);
    }

    public String getValueAsString() {
        return System.getProperty(propertyName, defaultVal);
    }

}
