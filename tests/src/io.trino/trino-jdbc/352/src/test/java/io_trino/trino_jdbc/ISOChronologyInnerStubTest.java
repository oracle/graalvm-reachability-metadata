/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.DateTimeZone;
import io.trino.jdbc.$internal.joda.time.chrono.ISOChronology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ISOChronologyInnerStubTest {
    @Test
    void restoresCachedChronologyAndZoneAwareFields() throws Exception {
        DateTimeZone zone = DateTimeZone.forOffsetHours(3);
        ISOChronology original = ISOChronology.getInstance(zone);
        ISOChronology restored = SerializationRoundTrip.copy(original);

        assertSame(original, restored);
        assertEquals(zone, restored.getZone());
        long leapDay = restored.getDateTimeMillis(2020, 2, 29, 12, 0, 0, 0);
        assertEquals(29, restored.dayOfMonth().get(leapDay));
        assertEquals(12, restored.hourOfDay().get(leapDay));
        assertEquals(28, restored.dayOfMonth().get(restored.year().add(leapDay, 1)));
        assertSame(ISOChronology.getInstanceUTC(), SerializationRoundTrip.copy(ISOChronology.getInstanceUTC()));
    }
}
