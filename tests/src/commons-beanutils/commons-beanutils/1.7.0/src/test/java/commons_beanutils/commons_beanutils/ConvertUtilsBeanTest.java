/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_beanutils.commons_beanutils;

import org.apache.commons.beanutils.ConvertUtilsBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConvertUtilsBeanTest {
    @Test
    void convertsScalarsAndCreatesTypedArrays() {
        ConvertUtilsBean converters = new ConvertUtilsBean();

        assertEquals(42, converters.convert("42", Integer.class));
        assertArrayEquals(new int[]{3, 5, 8},
                (int[]) converters.convert(new String[]{"3", "5", "8"}, int[].class));
        assertArrayEquals(new Integer[]{7, 11},
                (Integer[]) converters.convert(new String[]{"7", "11"}, Integer.class));
        assertArrayEquals(new String[]{"red", "blue"},
                (String[]) converters.convert(new String[]{"red", "blue"}, String[].class));
    }
}
