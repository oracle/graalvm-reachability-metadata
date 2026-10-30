/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.runtime.log.Log;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.util.introspection.SecureIntrospectorImpl;
import org.junit.jupiter.api.Test;

public class SecureIntrospectorImplTest {
    @Test
    void exercisesPublicApi() throws Exception {
        SecureIntrospectorImpl introspector = new SecureIntrospectorImpl(new String[]{System.class.getName()}, new String[]{"java.lang.reflect"}, new Log(new NullLogChute()));
        assertTrue(introspector.checkObjectExecutePermission(String.class, "substring"));
        assertFalse(introspector.checkObjectExecutePermission(System.class, "exit"));
        assertFalse(introspector.checkObjectExecutePermission(Object.class, "wait"));
        assertNull(introspector.getMethod(System.class, "exit", new Object[]{0}));
        assertEquals("substring", introspector.getMethod(String.class, "substring", new Object[]{1}).getName());
    }
}
