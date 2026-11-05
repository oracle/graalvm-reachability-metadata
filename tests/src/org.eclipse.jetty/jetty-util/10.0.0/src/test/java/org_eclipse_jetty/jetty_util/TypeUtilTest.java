/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.math.BigDecimal;
import java.net.URI;

import org.eclipse.jetty.util.TypeUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TypeUtilTest {
    @Test
    void convertsPrimitiveAndBoxedConfigurationValues() {
        Class<?>[] types = {boolean.class, byte.class, double.class, float.class,
                int.class, long.class, short.class,
                Boolean.class, Byte.class, Double.class, Float.class,
                Integer.class, Long.class, Short.class};
        String[] inputs = {"true", "12", "2.5", "3.5", "42", "1000", "24",
                "true", "12", "2.5", "3.5", "42", "1000", "24"};
        Object[] expected = {true, (byte) 12, 2.5d, 3.5f, 42, 1000L, (short) 24,
                true, (byte) 12, 2.5d, 3.5f, 42, 1000L, (short) 24};
        for (int i = 0; i < types.length; i++) {
            assertEquals(expected[i], TypeUtil.valueOf(types[i], inputs[i]));
        }
        assertEquals(81, TypeUtil.valueOf("int", "81"));
    }

    @Test
    void convertsValuesUsingStringConstructors() {
        assertEquals(new BigDecimal("19.95"), TypeUtil.valueOf(BigDecimal.class, "19.95"));
        assertEquals(URI.create("https://example.com/catalog"),
                TypeUtil.valueOf(URI.class, "https://example.com/catalog"));
    }

    @Test
    void locatesClassResourcesThroughTheirLoader() {
        URI location = TypeUtil.getClassLoaderLocation(TypeUtil.class);
        assertNotNull(location);
        assertNotNull(location.getScheme());
        assertEquals(location, TypeUtil.getClassLoaderLocation(TypeUtil.class, TypeUtil.class.getClassLoader()));
    }
}
