package perf.client;

import org.apache.commons.math.stat.StatUtils;
import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math.stat.descriptive.rank.Percentile;
import org.eclipse.jetty.client.api.Response;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Nitesh Kant
 */
public class ResultStatsCollector {

    private ConcurrentLinkedQueue<Double> responseTimesInMillis = new ConcurrentLinkedQueue<Double>();

    private AtomicLong failedRequestsOrResponse = new AtomicLong();
    private AtomicLong non200Responses = new AtomicLong();
    private AtomicLong responses200 = new AtomicLong();

    public void addResponseDetails(org.eclipse.jetty.client.api.Result result, long timeTakenInMillis) {
        if (result.isFailed()) {
            failedRequestsOrResponse.incrementAndGet();
            return;
        }
        Response response = result.getResponse();
        responseTimesInMillis.add((double) timeTakenInMillis);
        if (response.getStatus() >= 200 && response.getStatus() < 300) {
            responses200.incrementAndGet();
        } else {
            non200Responses.incrementAndGet();
        }
    }

    public StatsResult calculateResult(TestResult result) {
        StatsResult toReturn = new StatsResult();
        int sampleSize = responseTimesInMillis.size();
        Double[] times = new Double[sampleSize];
        responseTimesInMillis.toArray(times);
        responseTimesInMillis.clear();
        toReturn.sampleSize = times.length;

        if (0 < toReturn.sampleSize) {
            Percentile percentile = new Percentile();
            double[] unboxedValues = new double[times.length];
            for (int i = 0; i < times.length; i++) {
                Double time = times[i];
                unboxedValues[i] = time;
            }
            Arrays.sort(unboxedValues);
            toReturn.percentile_99_5 = percentile.evaluate(unboxedValues, 99.5);
            toReturn.percentile_99 = percentile.evaluate(unboxedValues, 99);
            toReturn.percentile_90 = percentile.evaluate(unboxedValues, 90);
            toReturn.median = percentile.evaluate(unboxedValues, 50);
            toReturn.max = StatUtils.max(unboxedValues);
            toReturn.mean = new Mean().evaluate(unboxedValues);
            toReturn.stddev = new StandardDeviation().evaluate(unboxedValues);
        }


        result.setTotal2XXResponses(responses200.get());
        result.setTotalRequestsSent(toReturn.getSampleSize());
        result.setStatsResult(toReturn);
        result.setTotalNon2XXResponses(non200Responses.get());
        result.setTotalFailedRequestsOrResponse(failedRequestsOrResponse.get());

        return toReturn;
    }

    public static class StatsResult {

        private int sampleSize;
        private double mean;
        private double median;
        private double percentile_99_5;
        private double percentile_99;
        private double percentile_90;
        private double stddev;
        private double max;

        public int getSampleSize() {
            return sampleSize;
        }

        public double getMean() {
            return mean;
        }

        public double getMedian() {
            return median;
        }

        public double getPercentile_99_5() {
            return percentile_99_5;
        }

        public double getPercentile_99() {
            return percentile_99;
        }

        public double getPercentile_90() {
            return percentile_90;
        }

        public double getStddev() {
            return stddev;
        }

        public double getMax() {
            return max;
        }
    }

}
