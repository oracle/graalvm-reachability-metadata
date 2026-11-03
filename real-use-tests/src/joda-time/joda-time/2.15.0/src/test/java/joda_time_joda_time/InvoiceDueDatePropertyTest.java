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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's date-time property subsystem for deriving invoice due dates from an issue timestamp.
public class InvoiceDueDatePropertyTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;

    @Test
    void derivesMonthEndDueDateAndNextBillingDateFromDateProperties() {
        DateTime invoiceIssued = new DateTime(2025, 2, 14, 16, 45, 30, 0, UTC);
        DateTime monthEnd = invoiceIssued.dayOfMonth().withMaximumValue();
        DateTime nextBillingDate = invoiceIssued.monthOfYear().addToCopy(1);
        DateTime nextMonthStart = nextBillingDate.dayOfMonth().withMinimumValue();

        assertEquals(new DateTime(2025, 2, 14, 16, 45, 30, 0, UTC), invoiceIssued);
        assertEquals(new DateTime(2025, 2, 28, 16, 45, 30, 0, UTC), monthEnd);
        assertEquals("February", invoiceIssued.monthOfYear().getAsText(Locale.US));
        assertEquals(28, invoiceIssued.dayOfMonth().getMaximumValue());
        assertEquals(new DateTime(2025, 3, 14, 16, 45, 30, 0, UTC), nextBillingDate);
        assertEquals(new DateTime(2025, 3, 1, 16, 45, 30, 0, UTC), nextMonthStart);
        assertEquals(14, invoiceIssued.dayOfMonth().get());
    }
}
