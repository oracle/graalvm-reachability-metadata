/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.joda.time.LocalDate;
import io.trino.jdbc.$internal.joda.time.format.DateTimeFormat;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTimeUtilsTest {
    @Test
    void formatsAndParsesLocalizedMonthNames() {
        LocalDate date = new LocalDate(2020, 3, 12);
        assertEquals("12 March 2020", DateTimeFormat.forPattern("dd MMMM yyyy").withLocale(Locale.ENGLISH).print(date));
        assertEquals(date, DateTimeFormat.forPattern("dd MMMM yyyy").withLocale(Locale.ENGLISH).parseLocalDate("12 March 2020"));
    }
}
