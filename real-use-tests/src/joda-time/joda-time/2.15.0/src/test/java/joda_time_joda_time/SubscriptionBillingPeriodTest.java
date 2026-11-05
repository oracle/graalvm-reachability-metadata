/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's period subsystem for calculating recurring subscription billing dates.
public class SubscriptionBillingPeriodTest {
    private static final DateTimeZone UTC = DateTimeZone.UTC;
    private static final PeriodFormatter BILLING_PERIOD_FORMAT = ISOPeriodFormat.standard();

    @Test
    void calculatesTheNextInvoiceAndRoundTripsTheBillingConfiguration() {
        Period monthlyBillingCycle = BILLING_PERIOD_FORMAT.parsePeriod("P1M");
        DateTime invoiceIssued = new DateTime(2024, 1, 31, 9, 0, UTC);
        DateTime nextInvoice = invoiceIssued.plus(monthlyBillingCycle);
        Period gracePeriod = Period.days(7).plusHours(12);

        assertEquals("P1M", BILLING_PERIOD_FORMAT.print(monthlyBillingCycle));
        assertEquals(1, monthlyBillingCycle.getMonths());
        assertEquals(new DateTime(2024, 2, 29, 9, 0, UTC), nextInvoice);
        assertEquals("P7DT12H", BILLING_PERIOD_FORMAT.print(gracePeriod));
        assertEquals(7 * 24 * 60 * 60 * 1000L + 12 * 60 * 60 * 1000L,
                gracePeriod.toStandardDuration().getMillis());
    }
}
