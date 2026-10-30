/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.converters.DecimalLocaleConverter;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecimalLocaleConverterTest {
    @Test
    void parsesGroupedDecimalValues() {
        DecimalLocaleConverter converter = new DecimalLocaleConverter(Locale.US, "#,##0.00");

        Number grouped = (Number) converter.convert("1,234.50");
        Number explicit = (Number) converter.convert(Number.class, "27.25", "0.00");

        assertEquals(1234.5, grouped.doubleValue());
        assertEquals(27.25, explicit.doubleValue());
    }
}
