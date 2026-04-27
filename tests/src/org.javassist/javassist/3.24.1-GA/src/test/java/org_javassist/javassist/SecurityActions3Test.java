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

import org.junit.jupiter.api.Test;

public class SecurityActions3Test {
    private static final String SECURITY_ACTIONS_CLASS = "javassist.util.proxy.SecurityActions";

    @Test
    void getMethodHandleUnreflectsPrivateMethod() throws Throwable {
        Class<?> securityActions = Class.forName(SECURITY_ACTIONS_CLASS);
        MethodHandle getMethodHandle = MethodHandles.privateLookupIn(securityActions, MethodHandles.lookup()).findStatic(
                securityActions,
                "getMethodHandle",
                MethodType.methodType(MethodHandle.class, Class.class, String.class, Class[].class));

        MethodHandle formatGreeting = (MethodHandle) getMethodHandle.invoke(
                HandleTarget.class,
                "formatGreeting",
                new Class<?>[] {String.class});

        assertThat((String) formatGreeting.invokeExact("Grace")).isEqualTo("Hello, Grace");
    }

    public static class HandleTarget {
        private static String formatGreeting(String name) {
            return "Hello, " + name;
        }
    }
}
