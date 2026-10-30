/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.converters.DateLocaleConverter;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateLocaleConverterTest {
    @Test
    void parsesDatesUsingConfiguredAndExplicitPatterns() {
        DateLocaleConverter converter = new DateLocaleConverter(Locale.US, "yyyy-MM-dd");
        Date parsed = (Date) converter.convert("2024-02-29");
        Calendar calendar = Calendar.getInstance(Locale.US);
        calendar.setTime(parsed);

        assertEquals(2024, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH));
        assertEquals(29, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(parsed, converter.convert(Date.class, "02/29/2024", "MM/dd/yyyy"));
    }
}
