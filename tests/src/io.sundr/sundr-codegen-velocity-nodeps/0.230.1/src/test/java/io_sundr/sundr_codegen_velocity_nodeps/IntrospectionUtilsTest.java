/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.util.introspection.IntrospectionUtils;
import org.junit.jupiter.api.Test;

public class IntrospectionUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        assertTrue(IntrospectionUtils.isMethodInvocationConvertible(long.class, Integer.class, false));
        assertTrue(IntrospectionUtils.isStrictMethodInvocationConvertible(long.class, int.class, false));
        assertTrue(IntrospectionUtils.isMethodInvocationConvertible(String[].class, String.class, true));
        assertFalse(IntrospectionUtils.isMethodInvocationConvertible(int.class, String.class, false));
    }
}
