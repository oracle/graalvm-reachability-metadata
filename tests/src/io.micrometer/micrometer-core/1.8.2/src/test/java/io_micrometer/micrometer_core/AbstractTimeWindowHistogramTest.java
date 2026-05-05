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
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AbstractTimeWindowHistogramTest {
    @Test
    void fixedBoundaryHistogramInitializesRingBufferAndReportsBucketCounts() {
        DistributionStatisticConfig config = DistributionStatisticConfig.builder()
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(100.0)
                .serviceLevelObjectives(10.0, 50.0)
                .expiry(Duration.ofSeconds(3))
                .bufferLength(3)
                .build();
        Histogram histogram = new TimeWindowFixedBoundaryHistogram(new MockClock(), config, false);

        histogram.recordLong(7);
        histogram.recordLong(42);
        HistogramSnapshot snapshot = histogram.takeSnapshot(2, 49.0, 42.0);

        assertThat(snapshot.count()).isEqualTo(2);
        assertThat(snapshot.total()).isEqualTo(49.0);
        assertThat(snapshot.max()).isEqualTo(42.0);
        assertThat(snapshot.histogramCounts())
                .extracting(CountAtBucket::bucket, CountAtBucket::count)
                .containsExactly(tuple(10.0, 1.0), tuple(50.0, 2.0));
    }
}
