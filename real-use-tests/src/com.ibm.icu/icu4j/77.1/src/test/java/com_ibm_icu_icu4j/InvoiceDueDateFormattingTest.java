/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's date-formatting subsystem to render invoice due dates for users in different locales.
public class InvoiceDueDateFormattingTest {
    @Test
    public void formatsAnInvoiceDueDateForCustomersInDifferentLocales() {
        Date dueDate = invoiceDueDate();

        assertEquals("January 15, 2025", formatDate(dueDate, ULocale.US));
        assertEquals("15 janvier 2025", formatDate(dueDate, ULocale.FRANCE));
    }

    private static Date invoiceDueDate() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), ULocale.US);
        calendar.clear();
        calendar.set(2025, Calendar.JANUARY, 15);
        return calendar.getTime();
    }

    private static String formatDate(Date date, ULocale locale) {
        DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG, locale);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }
}
