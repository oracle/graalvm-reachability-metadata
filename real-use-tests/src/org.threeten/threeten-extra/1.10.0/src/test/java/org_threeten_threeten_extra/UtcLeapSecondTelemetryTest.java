/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.scale.TaiInstant;
import org.threeten.extra.scale.UtcInstant;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the UTC time-scale subsystem to preserve a leap second in telemetry timestamps.
public class UtcLeapSecondTelemetryTest {
    @Test
    @Timeout(60)
    public void ordersLeapSecondTelemetryAndConvertsItToTai() {
        UtcInstant finalSecond = UtcInstant.parse("2016-12-31T23:59:59Z");
        UtcInstant leapSecond = UtcInstant.parse("2016-12-31T23:59:60Z");
        UtcInstant firstSecond = UtcInstant.parse("2017-01-01T00:00:00Z");

        TaiInstant taiLeapSecond = leapSecond.toTaiInstant();
        UtcInstant recoveredLeapSecond = UtcInstant.of(taiLeapSecond);

        assertTrue(leapSecond.isLeapSecond());
        assertTrue(finalSecond.isBefore(leapSecond));
        assertTrue(leapSecond.isBefore(firstSecond));
        assertEquals(Duration.ofSeconds(1), finalSecond.durationUntil(leapSecond));
        assertEquals(Duration.ofSeconds(1), leapSecond.durationUntil(firstSecond));
        assertEquals(Duration.ofSeconds(2), finalSecond.durationUntil(firstSecond));
        assertEquals(leapSecond, recoveredLeapSecond);
        assertEquals(taiLeapSecond, TaiInstant.of(leapSecond));
    }
}
