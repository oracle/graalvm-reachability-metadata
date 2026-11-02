/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Joda-Time's interval subsystem for reserving and checking customer support windows.
public class SupportWindowIntervalTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;

    @Test
    void identifiesOverlappingReservationsAndTheRemainingWindow() {
        DateTime windowStart = new DateTime(2025, 6, 12, 9, 0, UTC);
        Interval supportWindow = new Interval(windowStart, windowStart.plusHours(3));
        Interval customerReservation = new Interval(windowStart.plusHours(1), windowStart.plusHours(2));
        Interval followUpReservation = new Interval(windowStart.plusHours(3), windowStart.plusHours(4));

        assertTrue(supportWindow.contains(windowStart.plusMinutes(30)));
        assertFalse(supportWindow.contains(supportWindow.getEnd()));
        assertTrue(supportWindow.overlaps(customerReservation));
        assertFalse(supportWindow.overlaps(followUpReservation));

        Interval overlap = supportWindow.overlap(customerReservation);
        assertEquals(windowStart.plusHours(1), overlap.getStart());
        assertEquals(windowStart.plusHours(2), overlap.getEnd());
        assertEquals(60 * 60 * 1000L, overlap.toDurationMillis());

        Interval remainingWindow = new Interval(windowStart, customerReservation.getStart());
        assertEquals(60 * 60 * 1000L, remainingWindow.toDurationMillis());
        assertEquals(windowStart, remainingWindow.getStart());
        assertEquals(customerReservation.getStart(), remainingWindow.getEnd());
    }
}
