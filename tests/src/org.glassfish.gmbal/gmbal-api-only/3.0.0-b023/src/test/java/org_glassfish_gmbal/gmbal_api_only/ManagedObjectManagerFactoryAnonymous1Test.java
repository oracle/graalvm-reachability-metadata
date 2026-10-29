/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_gmbal.gmbal_api_only;

import java.lang.reflect.Method;

import org.glassfish.gmbal.ManagedObjectManagerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ManagedObjectManagerFactoryAnonymous1Test {
    @Test
    void resolvesAndInvokesAMethodWithPrimitiveParameters() throws ReflectiveOperationException {
        Method substring = ManagedObjectManagerFactory.getMethod(String.class, "substring", int.class, int.class);

        assertEquals(String.class, substring.getDeclaringClass());
        assertEquals(String.class, substring.getReturnType());
        assertEquals("object", substring.invoke("managed-object", 8, 14));
    }

    @Test
    void resolvesAndInvokesAMethodWithoutParameters() throws ReflectiveOperationException {
        Method isEmpty = ManagedObjectManagerFactory.getMethod(String.class, "isEmpty");

        assertEquals(boolean.class, isEmpty.getReturnType());
        assertEquals(0, isEmpty.getParameterCount());
        assertEquals(true, isEmpty.invoke(""));
        assertEquals(false, isEmpty.invoke("managed"));
    }
}
