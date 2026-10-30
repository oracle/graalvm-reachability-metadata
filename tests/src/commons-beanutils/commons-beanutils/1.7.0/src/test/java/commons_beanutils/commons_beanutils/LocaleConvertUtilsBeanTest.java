/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.LocaleConvertUtilsBean;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocaleConvertUtilsBeanTest {
    @Test
    void convertsLocaleSensitiveScalarsAndTypedArrays() {
        LocaleConvertUtilsBean converters = new LocaleConvertUtilsBean();
        converters.setDefaultLocale(Locale.US);

        assertEquals(1234, converters.convert("1,234", Integer.class, "#,##0"));
        assertArrayEquals(new Integer[]{1234, 2500},
                (Integer[]) converters.convert(new String[]{"1,234", "2,500"},
                        Integer[].class, Locale.US, "#,##0"));
        assertArrayEquals(new int[]{12, 34},
                (int[]) converters.convert(new String[]{"12", "34"}, int.class, Locale.US, "0"));
        assertEquals("1,234.50", converters.convert(Double.valueOf(1234.5), Locale.US, "#,##0.00"));
    }
}
