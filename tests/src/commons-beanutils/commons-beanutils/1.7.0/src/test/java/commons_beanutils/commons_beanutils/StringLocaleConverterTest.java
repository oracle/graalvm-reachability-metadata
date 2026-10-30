/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.converters.StringLocaleConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringLocaleConverterTest {
    @Test
    void formatsNumbersDatesAndText() {
        StringLocaleConverter converter = new StringLocaleConverter(Locale.US, "#,##0.00");

        assertEquals("1,234.50", converter.convert(String.class, new BigDecimal("1234.50")));
        assertEquals("2,048", converter.convert(String.class, Long.valueOf(2048), "#,##0"));
        Calendar calendar = new GregorianCalendar(2024, Calendar.JANUARY, 9);
        assertEquals("2024-01-09", converter.convert(String.class, calendar.getTime(), "yyyy-MM-dd"));
        assertEquals("ready", converter.convert("ready"));
    }
}
