package perf.test.utils.netty;

import org.junit.Test;

import java.util.Random;

/**
 * @author mhawthorne
 */
public class Sampler {

    private static final Random random = new Random();

    public static final boolean shouldSample(int samplePercentage) {
        return shouldSample(randomLong(), samplePercentage);
    }

    public static final boolean shouldSample(long seed, int samplePercentage) {
        if (samplePercentage >= 100) return true;
        else if (samplePercentage <= 0) return false;

        boolean result;
        if (samplePercentage <= 50) {
            final int mod = Math.round((float) 100 / samplePercentage);
            final long rawResult = seed % mod;
            result = rawResult == 0;
        } else {
            final int mod = Math.round((float) 100 / (100 - samplePercentage));
            final long rawResult = seed % mod;
            result = rawResult != 0;
        }
        return result;
    }

    private static final long randomLong() {
//        return randomize(System.nanoTime());
        return Math.abs(random.nextLong());
    }

    // ripped from:
    // http://www.javamex.com/tutorials/random_numbers/xorshift.shtml#.UrDOU2RDsao
    private static final long randomize(long x) {
        x ^= (x << 21);
        x ^= (x >>> 35);
        x ^= (x << 4);
        return x;
    }

    public static final class UnitTest {

        @Test
        public void testShouldSample() {
            test(100);
            test(75);
            test(50);
            test(25);
            test(10);
            test(1);
            test(0);
        }

        private static final void test(int desiredPercentage) {
            final int iterations = 10000;
            int samples = 0;
            for (int i = 0; i < iterations; i++) {
                final boolean b = Sampler.shouldSample(Sampler.randomLong(), desiredPercentage);
                if (b) samples++;
            }

            final int actualPercentage = (int) ((float) samples / iterations * 100);
            System.out.println(String.format("desired:%s, actual:%s", desiredPercentage, actualPercentage));
        }
    }
}
