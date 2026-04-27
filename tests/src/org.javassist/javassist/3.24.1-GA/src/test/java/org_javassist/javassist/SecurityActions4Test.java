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
import java.lang.reflect.Method;
import java.security.PrivilegedExceptionAction;

import org.junit.jupiter.api.Test;

public class SecurityActions4Test {
    private static final String GET_DECLARED_METHOD_ACTION_CLASS = "javassist.util.proxy.SecurityActions$4";

    @Test
    void privilegedExceptionActionReadsNamedDeclaredMethod() throws Throwable {
        Class<?> actionClass = Class.forName(GET_DECLARED_METHOD_ACTION_CLASS);
        MethodHandle constructor = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup()).findConstructor(
                actionClass,
                MethodType.methodType(void.class, Class.class, String.class, Class[].class));
        PrivilegedExceptionAction<?> action = (PrivilegedExceptionAction<?>) constructor.invoke(
                MethodLookupTarget.class,
                "formatMessage",
                new Class<?>[] {String.class});

        Method method = (Method) action.run();

        assertThat(method.getDeclaringClass()).isEqualTo(MethodLookupTarget.class);
        assertThat(method.getName()).isEqualTo("formatMessage");
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    public static class MethodLookupTarget {
        private String formatMessage(String name) {
            return "Hello, " + name;
        }
    }
}
