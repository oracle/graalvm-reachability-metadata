/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClockAnonymous1Test {

    @Test
    void defaultRegistryTimesWorkUsingSystemClock() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            Timer timer = registry.timer("operation.duration");
            String result = timer.record(() -> "measured");

            assertThat(result).isEqualTo("measured");
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0.0);
            assertThat(registry.config().clock().wallTime()).isGreaterThan(0L);
        } finally {
            registry.close();
        }
    }
}
