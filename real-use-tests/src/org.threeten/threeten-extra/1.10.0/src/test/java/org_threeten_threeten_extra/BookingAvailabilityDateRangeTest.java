/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.LocalDateRange;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the local-date range subsystem to reconcile adjacent booking windows.
public class BookingAvailabilityDateRangeTest {
    @Test
    @Timeout(60)
    public void combinesBookingWindowsAndListsAvailableDates() {
        LocalDateRange initialBookingWindow = LocalDateRange.parse("2025-06-10/2025-06-15");
        LocalDateRange extensionWindow = LocalDateRange.ofClosed(
                LocalDate.of(2025, 6, 15),
                LocalDate.of(2025, 6, 18));

        assertEquals(LocalDate.of(2025, 6, 10), initialBookingWindow.getStart());
        assertEquals(LocalDate.of(2025, 6, 14), initialBookingWindow.getEndInclusive());
        assertEquals(5, initialBookingWindow.lengthInDays());
        assertEquals(
                List.of(
                        LocalDate.of(2025, 6, 10),
                        LocalDate.of(2025, 6, 11),
                        LocalDate.of(2025, 6, 12),
                        LocalDate.of(2025, 6, 13),
                        LocalDate.of(2025, 6, 14)),
                initialBookingWindow.stream().toList());

        assertTrue(initialBookingWindow.abuts(extensionWindow));
        assertTrue(initialBookingWindow.isConnected(extensionWindow));
        assertFalse(initialBookingWindow.overlaps(extensionWindow));
        assertEquals(
                LocalDateRange.parse("2025-06-10/2025-06-19"),
                initialBookingWindow.union(extensionWindow));
        assertEquals(
                LocalDateRange.parse("2025-06-10/2025-06-19"),
                initialBookingWindow.span(extensionWindow));
        assertEquals(LocalDate.of(2025, 6, 18), extensionWindow.getEndInclusive());
    }
}
