package perf.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Nitesh Kant
 */
public class TestResult {

    private ResultStatsCollector.StatsResult statsResult;
    private ConcurrentLinkedQueue<IndividualResult> individualResults = new ConcurrentLinkedQueue<IndividualResult>();
    private String testUri;
    private int concurrentClients;
    private long totalRequestsSent;
    private long total2XXResponses;
    private long totalNon2XXResponses;
    private long totalFailedRequestsOrResponse;

    public ResultStatsCollector.StatsResult getStatsResult() {
        return statsResult;
    }

    public void setStatsResult(ResultStatsCollector.StatsResult statsResult) {
        this.statsResult = statsResult;
    }

    public ConcurrentLinkedQueue<IndividualResult> getIndividualResults() {
        return individualResults;
    }

    public String getTestUri() {
        return testUri;
    }

    public void setTestUri(String testUri) {
        this.testUri = testUri;
    }

    public int getConcurrentClients() {
        return concurrentClients;
    }

    public void setConcurrentClients(int concurrentClients) {
        this.concurrentClients = concurrentClients;
    }

    public long getTotalRequestsSent() {
        return totalRequestsSent;
    }

    public void setTotalRequestsSent(long totalRequestsSent) {
        this.totalRequestsSent = totalRequestsSent;
    }

    public long getTotal2XXResponses() {
        return total2XXResponses;
    }

    public void setTotal2XXResponses(long total2XXResponses) {
        this.total2XXResponses = total2XXResponses;
    }

    public long getTotalNon2XXResponses() {
        return totalNon2XXResponses;
    }

    public void setTotalNon2XXResponses(long totalNon2XXResponses) {
        this.totalNon2XXResponses = totalNon2XXResponses;
    }

    public long getTotalFailedRequestsOrResponse() {
        return totalFailedRequestsOrResponse;
    }

    public void setTotalFailedRequestsOrResponse(long totalFailedRequestsOrResponse) {
        this.totalFailedRequestsOrResponse = totalFailedRequestsOrResponse;
    }

    public String toJson() {
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public static class IndividualResult {

        private String totalTime;
        private String serverTime;
        private String loadAvgPerCore;

        public String getTotalTime() {
            return totalTime;
        }

        public void setTotalTime(String totalTime) {
            this.totalTime = totalTime;
        }

        public String getServerTime() {
            return serverTime;
        }

        public void setServerTime(String serverTime) {
            this.serverTime = serverTime;
        }

        public String getLoadAvgPerCore() {
            return loadAvgPerCore;
        }

        public void setLoadAvgPerCore(String loadAvgPerCore) {
            this.loadAvgPerCore = loadAvgPerCore;
        }
    }
}
