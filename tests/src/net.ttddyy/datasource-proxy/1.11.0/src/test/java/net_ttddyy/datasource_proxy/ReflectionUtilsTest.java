/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.proxy.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ReflectionUtilsTest {
    @Test
    void discoversAnOptionalJdbcCapabilityByItsSignature() {
        Method setter = ReflectionUtils.getMethodIfAvailable(
                PreparedStatement.class, "setNString", int.class, String.class);
        assertNotNull(setter);
        assertEquals("setNString", setter.getName());
        assertEquals(PreparedStatement.class, setter.getDeclaringClass());
        assertArrayEquals(new Class<?>[]{int.class, String.class}, setter.getParameterTypes());
        assertNull(ReflectionUtils.getMethodIfAvailable(PreparedStatement.class, "setApplicationTag", String.class));
    }
}
