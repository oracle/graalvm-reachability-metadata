/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import java.util.Locale;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's date-time formatting subsystem for parsing audit-log timestamps and rendering a stable API response.
public class AuditLogTimestampFormattingTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;
    private static final DateTimeFormatter AUDIT_LOG_FORMAT = DateTimeFormat
            .forPattern("dd/MMM/yyyy HH:mm:ss Z")
            .withLocale(Locale.US)
            .withZone(UTC);
    private static final DateTimeFormatter API_RESPONSE_FORMAT = ISODateTimeFormat
            .dateTime()
            .withZone(UTC);

    @Test
    void parsesAnAuditTimestampAndRendersAStableResponseValue() {
        DateTime event = AUDIT_LOG_FORMAT.parseDateTime("14/Mar/2025 16:45:30 +0000");

        assertEquals(2025, event.getYear());
        assertEquals(3, event.getMonthOfYear());
        assertEquals(14, event.getDayOfMonth());
        assertEquals("2025-03-14T16:45:30.000Z", API_RESPONSE_FORMAT.print(event));
        assertEquals("14/Mar/2025 16:45:30 +0000", AUDIT_LOG_FORMAT.print(event));
    }
}
