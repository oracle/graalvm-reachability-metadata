/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_java8_compat_2_13;

import org.junit.jupiter.api.Test;
import scala.compat.java8.DurationConverters;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DurationConvertersTest {
    @Test
    void preservesZeroPositiveNegativeAndFractionalDurations() {
        List<Duration> durations = List.of(Duration.ZERO, Duration.ofSeconds(19), Duration.ofNanos(47),
                Duration.ofMillis(1_500), Duration.ofMillis(-1_500), Duration.ofSeconds(-3));
        for (Duration duration : durations) {
            FiniteDuration converted = DurationConverters.toScala(duration);
            assertEquals(duration.toNanos(), converted.toNanos());
            assertEquals(duration, DurationConverters.toJava(converted));
        }
    }

    @Test
    void convertsEveryScalaTimeUnit() {
        for (TimeUnit unit : TimeUnit.values()) {
            FiniteDuration duration = new FiniteDuration(7, unit);
            assertEquals(Duration.ofNanos(unit.toNanos(7)), DurationConverters.toJava(duration));
        }
    }

    @Test
    void rejectsJavaDurationsOutsideTheScalaRange() {
        assertThrows(IllegalArgumentException.class,
                () -> DurationConverters.toScala(Duration.ofSeconds(Long.MAX_VALUE, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> DurationConverters.toScala(Duration.ofSeconds(Long.MIN_VALUE)));
    }
}
