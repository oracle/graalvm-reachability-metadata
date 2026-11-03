/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Joda-Time's instant subsystem for ordering an event received from an external system and deriving its expiry time.
public class EventIngestionInstantTest {
    private static final DateTimeZone NEW_YORK = DateTimeZone.forID("America/New_York");

    @Test
    void ordersReceivedEventAndCalculatesExpiryAcrossTimeZones() {
        Instant receivedAt = Instant.parse("2025-03-14T16:45:30.000Z");
        Instant expiresAt = receivedAt.plus(Duration.standardMinutes(15));
        DateTime localReceivedAt = receivedAt.toDateTime(NEW_YORK);

        assertEquals("2025-03-14T16:45:30.000Z", receivedAt.toString());
        assertEquals(1741970730000L, receivedAt.getMillis());
        assertTrue(receivedAt.isBefore(expiresAt));
        assertTrue(expiresAt.isAfter(receivedAt));
        assertEquals("2025-03-14T12:45:30.000-04:00", localReceivedAt.toString());
        assertEquals(900_000L, expiresAt.getMillis() - receivedAt.getMillis());
    }
}
