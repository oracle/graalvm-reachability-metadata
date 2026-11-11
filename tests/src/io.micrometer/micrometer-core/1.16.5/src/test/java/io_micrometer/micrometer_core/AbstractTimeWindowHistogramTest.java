/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.time.Duration;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTimeWindowHistogramTest {

    @Test
    void recordsPercentilesAndExpiresOldSamples() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        try {
            DistributionSummary summary = DistributionSummary.builder("payload.size")
                    .publishPercentiles(0.5, 0.9)
                    .distributionStatisticBufferLength(3)
                    .distributionStatisticExpiry(Duration.ofMinutes(1))
                    .register(registry);
            for (int value = 1; value <= 100; value++) {
                summary.record(value);
            }

            HistogramSnapshot snapshot = summary.takeSnapshot();
            assertThat(snapshot.count()).isEqualTo(100);
            assertThat(snapshot.total()).isEqualTo(5050.0);
            assertThat(snapshot.percentileValues()).hasSize(2);
            assertThat(snapshot.percentileValues()[0].value()).isBetween(48.0, 52.0);
            assertThat(snapshot.percentileValues()[1].value()).isBetween(88.0, 94.0);

            clock.add(Duration.ofMinutes(1));
            assertThat(summary.takeSnapshot().percentileValues()[0].value()).isZero();
            summary.record(20);
            assertThat(summary.takeSnapshot().percentileValues()[0].value()).isBetween(19.0, 21.0);
        } finally {
            registry.close();
        }
    }
}
