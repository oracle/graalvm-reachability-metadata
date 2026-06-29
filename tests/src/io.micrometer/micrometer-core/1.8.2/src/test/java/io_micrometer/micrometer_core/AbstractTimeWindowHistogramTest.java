/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractTimeWindowHistogramTest {
    @Test
    void timerWithPublishedPercentilesRecordsTimeWindowHistogramSnapshot() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Timer timer = Timer.builder("test.time.window.histogram")
                    .publishPercentiles(0.5, 0.95)
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofSeconds(1))
                    .register(registry);

            timer.record(Duration.ofMillis(10));
            timer.record(Duration.ofMillis(100));
            timer.record(Duration.ofMillis(250));

            HistogramSnapshot snapshot = timer.takeSnapshot();

            assertThat(snapshot.count()).isEqualTo(3);
            assertThat(snapshot.total(TimeUnit.MILLISECONDS)).isEqualTo(360.0);
            assertThat(snapshot.max(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
            assertThat(snapshot.percentileValues())
                    .extracting(ValueAtPercentile::percentile)
                    .containsExactly(0.5, 0.95);
            assertThat(snapshot.percentileValues())
                    .extracting(value -> value.value(TimeUnit.MILLISECONDS))
                    .allSatisfy(value -> assertThat((Double) value).isPositive());
        } finally {
            registry.close();
        }
    }
}
