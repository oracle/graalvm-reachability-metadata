/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.MutableClock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the mutable-clock subsystem to drive deterministic order expiry timestamps.
public class MutableClockOrderExpiryTest {
    @Test
    @Timeout(60)
    public void advancesOrderClockAndReadsExpiryInDifferentZones() {
        MutableClock clock = MutableClock.of(
                Instant.parse("2025-06-01T09:15:00Z"),
                ZoneOffset.UTC);
        Instant orderCreatedAt = Instant.now(clock);
        LocalDateTime initialLocalTime = LocalDateTime.now(clock);
        LocalDateTime expectedExpiry = initialLocalTime.plusDays(2);

        clock.add(Duration.ofHours(2));
        Instant paymentCapturedAt = Instant.now(clock);
        clock.add(1, ChronoUnit.DAYS);
        Clock pacificClock = clock.withZone(ZoneOffset.ofHours(-7));

        assertEquals(Instant.parse("2025-06-01T09:15:00Z"), orderCreatedAt);
        assertEquals(LocalDateTime.of(2025, 6, 1, 9, 15), initialLocalTime);
        assertEquals(LocalDateTime.of(2025, 6, 3, 9, 15), expectedExpiry);
        assertEquals(Instant.parse("2025-06-01T11:15:00Z"), paymentCapturedAt);
        assertEquals(Instant.parse("2025-06-02T11:15:00Z"), clock.instant());
        assertEquals(
                LocalDateTime.of(2025, 6, 2, 4, 15),
                LocalDateTime.now(pacificClock));
    }
}
