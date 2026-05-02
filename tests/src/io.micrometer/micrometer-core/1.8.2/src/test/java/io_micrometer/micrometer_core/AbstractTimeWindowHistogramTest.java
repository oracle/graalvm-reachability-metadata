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
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTimeWindowHistogramTest {
    @Test
    void fixedBoundaryHistogramAllocatesRingBufferAndCountsConfiguredBuckets() {
        MockClock clock = new MockClock();
        DistributionStatisticConfig config = DistributionStatisticConfig.builder()
                .serviceLevelObjectives(10.0, 100.0)
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(1_000.0)
                .expiry(Duration.ofSeconds(3))
                .bufferLength(3)
                .build();
        TimeWindowFixedBoundaryHistogram histogram = new TimeWindowFixedBoundaryHistogram(clock, config, false);

        histogram.recordLong(7);
        histogram.recordLong(42);
        histogram.recordLong(150);

        HistogramSnapshot snapshot = histogram.takeSnapshot(3, 199.0, 150.0);
        CountAtBucket[] counts = snapshot.histogramCounts();

        assertThat(snapshot.count()).isEqualTo(3);
        assertThat(snapshot.total()).isEqualTo(199.0);
        assertThat(snapshot.max()).isEqualTo(150.0);
        assertThat(counts).hasSize(2);
        assertThat(counts[0].bucket()).isEqualTo(10.0);
        assertThat(counts[0].count()).isEqualTo(1.0);
        assertThat(counts[1].bucket()).isEqualTo(100.0);
        assertThat(counts[1].count()).isEqualTo(2.0);
    }
}
