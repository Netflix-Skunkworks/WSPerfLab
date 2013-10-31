package perf.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant
 */
public class TestResult {

    private ResultStatsCollector.StatsResult totalTimeStats;
    private ResultStatsCollector.StatsResult serverTimeStats;
    private ConcurrentLinkedQueue<IndividualResult> individualResults = new ConcurrentLinkedQueue<IndividualResult>();
    private String testUri;
    private int concurrentClients;
    private AtomicLong totalRequestsSent = new AtomicLong();
    private long total2XXResponses;
    private long totalNon2XXResponses;
    private long totalFailedRequestsOrResponse;
    private boolean collectIndividualResults = true;
    private Map<Integer, AtomicLong> respCodeVsCount;

    public ResultStatsCollector.StatsResult getServerTimeStats() {
        return serverTimeStats;
    }

    public void setServerTimeStats(ResultStatsCollector.StatsResult serverTimeStats) {
        this.serverTimeStats = serverTimeStats;
    }

    public ResultStatsCollector.StatsResult getTotalTimeStats() {
        return totalTimeStats;
    }

    public void setTotalTimeStats(ResultStatsCollector.StatsResult totalTimeStats) {
        this.totalTimeStats = totalTimeStats;
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
        return totalRequestsSent.get();
    }

    public void incrementRequestSent() {
        this.totalRequestsSent.incrementAndGet();
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

    public void setRespCodeVsCount(Map<Integer, AtomicLong> respCodeVsCount) {
        this.respCodeVsCount = respCodeVsCount;
    }

    public Map<Integer, AtomicLong> getRespCodeVsCount() {
        return respCodeVsCount;
    }

    public String toJson() {
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public boolean isCollectIndividualResults() {
        return collectIndividualResults;
    }

    public void setCollectIndividualResults(boolean collectIndividualResults) {
        this.collectIndividualResults = collectIndividualResults;
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
