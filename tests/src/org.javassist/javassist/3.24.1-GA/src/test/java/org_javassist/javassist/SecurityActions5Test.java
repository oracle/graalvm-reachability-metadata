/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.security.PrivilegedExceptionAction;

import org.junit.jupiter.api.Test;

public class SecurityActions5Test {
    private static final String GET_DECLARED_CONSTRUCTOR_ACTION_CLASS = "javassist.util.proxy.SecurityActions$5";

    @Test
    void privilegedExceptionActionReadsNamedDeclaredConstructor() throws Throwable {
        Class<?> actionClass = Class.forName(GET_DECLARED_CONSTRUCTOR_ACTION_CLASS);
        MethodHandle constructor = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup()).findConstructor(
                actionClass,
                MethodType.methodType(void.class, Class.class, Class[].class));
        PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) constructor.invoke(
                ConstructorLookupTarget.class,
                new Class<?>[] {String.class, int.class});

        Constructor<?> selectedConstructor = (Constructor<?>) action.run();

        assertThat(selectedConstructor.getDeclaringClass()).isEqualTo(ConstructorLookupTarget.class);
        assertThat(selectedConstructor.getParameterTypes()).containsExactly(String.class, int.class);
    }

    public static class ConstructorLookupTarget {
        public ConstructorLookupTarget() {
        }

        private ConstructorLookupTarget(String name, int priority) {
        }
    }
}
