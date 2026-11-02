/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten_threeten_extra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.threeten.extra.Interval;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the interval scheduling subsystem to combine delivery and maintenance windows.
public class DeliveryWindowIntervalTest {
    @Test
    @Timeout(60)
    public void combinesOverlappingWindowsAndPreservesHalfOpenBoundaries() {
        Interval deliveryWindow = Interval.parse("2024-06-10T09:00:00Z/2024-06-10T12:00:00Z");
        Interval maintenanceWindow = Interval.of(
                Instant.parse("2024-06-10T10:00:00Z"),
                Duration.ofHours(1));
        Interval eveningWindow = Interval.of(
                Instant.parse("2024-06-10T12:00:00Z"),
                Instant.parse("2024-06-10T13:00:00Z"));

        assertTrue(deliveryWindow.encloses(maintenanceWindow));
        assertTrue(deliveryWindow.overlaps(maintenanceWindow));
        assertEquals(maintenanceWindow, deliveryWindow.intersection(maintenanceWindow));
        assertEquals(Duration.ofHours(1), maintenanceWindow.toDuration());

        assertTrue(deliveryWindow.isConnected(eveningWindow));
        assertTrue(deliveryWindow.abuts(eveningWindow));
        assertEquals(
                Interval.parse("2024-06-10T09:00:00Z/2024-06-10T13:00:00Z"),
                deliveryWindow.union(eveningWindow));
        assertTrue(deliveryWindow.contains(Instant.parse("2024-06-10T09:00:00Z")));
        assertFalse(deliveryWindow.contains(Instant.parse("2024-06-10T12:00:00Z")));
    }
}
