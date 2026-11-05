/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mortbay.util.TypeUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeUtilTest {
    @Test
    public void convertsPrimitiveAndBoxedConfigurationValues() {
        Class<?>[] types = {boolean.class, Boolean.class, byte.class, Byte.class,
                double.class, Double.class, float.class, Float.class, int.class, Integer.class,
                long.class, Long.class, short.class, Short.class};
        String[] values = {"true", "true", "12", "12", "1.25", "1.25", "2.5", "2.5",
                "42", "42", "9000", "9000", "120", "120"};
        Object[] expected = {true, true, (byte) 12, (byte) 12, 1.25d, 1.25d, 2.5f, 2.5f,
                42, 42, 9000L, 9000L, (short) 120, (short) 120};
        for (int i = 0; i < types.length; i++) {
            assertEquals(expected[i], TypeUtil.valueOf(types[i], values[i]), types[i].getName());
        }
        assertEquals(42, TypeUtil.valueOf("int", "42"));
        assertEquals('J', TypeUtil.valueOf(char.class, "Jetty"));
        assertEquals("payload", TypeUtil.valueOf(String.class, "payload"));
    }

    @Test
    public void constructsValuesWithPublicStringConstructors() {
        assertEquals(new BigDecimal("123.45"), TypeUtil.valueOf(BigDecimal.class, "123.45"));
    }
}
