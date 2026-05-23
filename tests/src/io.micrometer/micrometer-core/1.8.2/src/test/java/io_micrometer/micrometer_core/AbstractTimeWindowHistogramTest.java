/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTimeWindowHistogramTest {
    @Test
    void recordsFixedBoundaryHistogramBucketsForTimerServiceLevelObjectives() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        try {
            Timer timer = Timer.builder("http.server.requests")
                    .serviceLevelObjectives(Duration.ofMillis(10), Duration.ofMillis(20))
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofSeconds(1))
                    .distributionStatisticExpiry(Duration.ofSeconds(3))
                    .distributionStatisticBufferLength(3)
                    .register(registry);

            timer.record(7, TimeUnit.MILLISECONDS);
            timer.record(20, TimeUnit.MILLISECONDS);

            HistogramSnapshot snapshot = timer.takeSnapshot();
            CountAtBucket[] buckets = snapshot.histogramCounts();

            assertThat(snapshot.count()).isEqualTo(2L);
            assertThat(buckets).hasSize(2);
            assertThat(buckets[0].bucket(TimeUnit.MILLISECONDS)).isEqualTo(10.0d);
            assertThat(buckets[0].count()).isEqualTo(1.0d);
            assertThat(buckets[1].bucket(TimeUnit.MILLISECONDS)).isEqualTo(20.0d);
            assertThat(buckets[1].count()).isEqualTo(2.0d);
        } finally {
            registry.close();
        }
    }
}
