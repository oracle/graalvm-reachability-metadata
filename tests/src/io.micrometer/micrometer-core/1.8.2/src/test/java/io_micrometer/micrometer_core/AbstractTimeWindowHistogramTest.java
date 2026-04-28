/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class AbstractTimeWindowHistogramTest {
    @Test
    void fixedBoundaryHistogramCreatesRingBufferAndCountsConfiguredBuckets() {
        TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(
                new MockClock(), fixedBoundaryHistogramConfig(), true);

        histogram.recordLong(7);
        histogram.recordLong(11);
        histogram.recordLong(50);

        HistogramSnapshot snapshot = histogram.takeSnapshot(3, 68, 50);

        assertThat(snapshot.histogramCounts())
                .extracting(CountAtBucket::bucket, CountAtBucket::count)
                .containsExactly(
                        bucketCount(10.0, 1.0),
                        bucketCount(50.0, 3.0));
    }

    private static DistributionStatisticConfig fixedBoundaryHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(100.0)
                .serviceLevelObjectives(10.0, 50.0)
                .expiry(Duration.ofSeconds(3))
                .bufferLength(3)
                .build();
    }

    private static Tuple bucketCount(double bucket, double count) {
        return tuple(bucket, count);
    }
}
