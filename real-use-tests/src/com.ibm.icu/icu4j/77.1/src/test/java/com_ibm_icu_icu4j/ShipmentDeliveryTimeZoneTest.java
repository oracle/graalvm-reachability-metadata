/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.TimeZoneNames;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's time-zone subsystem to schedule deliveries across daylight-saving transitions.
public class ShipmentDeliveryTimeZoneTest {
    @Test
    public void resolvesDestinationOffsetsAndNamesForDeliveryDates() {
        DeliveryScheduler scheduler = new DeliveryScheduler("America/New_York");

        assertEquals(-18_000_000, scheduler.offsetAt("2025-01-15T12:00:00Z"));
        assertEquals(-14_400_000, scheduler.offsetAt("2025-07-15T12:00:00Z"));
        assertEquals(
                "Eastern Standard Time",
                scheduler.zoneNameAt("2025-01-15T12:00:00Z", TimeZoneNames.NameType.LONG_STANDARD));
        assertEquals(
                "Eastern Daylight Time",
                scheduler.zoneNameAt("2025-07-15T12:00:00Z", TimeZoneNames.NameType.LONG_DAYLIGHT));
    }

    private static final class DeliveryScheduler {
        private final TimeZone destinationZone;
        private final TimeZoneNames zoneNames = TimeZoneNames.getInstance(ULocale.ENGLISH);

        private DeliveryScheduler(String destinationZoneId) {
            destinationZone = TimeZone.getTimeZone(destinationZoneId);
        }

        private int offsetAt(String utcTimestamp) {
            return destinationZone.getOffset(Instant.parse(utcTimestamp).toEpochMilli());
        }

        private String zoneNameAt(String utcTimestamp, TimeZoneNames.NameType nameType) {
            return zoneNames.getDisplayName(
                    destinationZone.getID(), nameType, Instant.parse(utcTimestamp).toEpochMilli());
        }
    }
}
