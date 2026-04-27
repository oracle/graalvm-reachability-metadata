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
import java.security.PrivilegedAction;

import org.junit.jupiter.api.Test;

public class SecurityActions1Test {
    private static final String GET_DECLARED_METHODS_ACTION_CLASS = "javassist.util.proxy.SecurityActions$1";

    @Test
    void privilegedActionReadsDeclaredMethods() throws Throwable {
        Class<?> actionClass = Class.forName(GET_DECLARED_METHODS_ACTION_CLASS);
        MethodHandle constructor = MethodHandles.privateLookupIn(actionClass, MethodHandles.lookup())
                .findConstructor(actionClass, MethodType.methodType(void.class, Class.class));
        PrivilegedAction<?> action = (PrivilegedAction<?>) constructor.invoke(MethodLookupTarget.class);

        Method[] methods = (Method[]) action.run();

        assertThat(methods).extracting(Method::getName).contains("message");
    }

    public static class MethodLookupTarget {
        public String message() {
            return "covered";
        }
    }
}
