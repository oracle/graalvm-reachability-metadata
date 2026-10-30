/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.locale.BaseLocaleConverter;
import org.apache.commons.beanutils.locale.converters.IntegerLocaleConverter;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BaseLocaleConverterTest {
    @Test
    void dispatchesConversionsUsingDefaultAndExplicitPatterns() {
        BaseLocaleConverter converter = new IntegerLocaleConverter(Locale.US, "#,##0");

        assertEquals(1234, converter.convert("1,234"));
        assertEquals(27, converter.convert(Integer.class, "27", "0"));
        assertNull(converter.convert((Object) null));

        BaseLocaleConverter withDefault = new IntegerLocaleConverter(Integer.valueOf(-1), Locale.US);
        assertEquals(-1, withDefault.convert((Object) null));
    }
}
