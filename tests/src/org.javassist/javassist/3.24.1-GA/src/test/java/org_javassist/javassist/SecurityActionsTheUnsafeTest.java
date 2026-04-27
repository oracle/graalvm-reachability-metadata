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

public class SecurityActionsTheUnsafeTest {
    private static final String SECURITY_ACTIONS_CLASS = "javassist.util.proxy.SecurityActions";
    private static final String THE_UNSAFE_CLASS = "javassist.util.proxy.SecurityActions$TheUnsafe";

    @Test
    void getsUnsafeAndInvokesCachedUnsafeMethod() throws Throwable {
        Class<?> securityActions = Class.forName(SECURITY_ACTIONS_CLASS);
        Class<?> theUnsafeClass = Class.forName(THE_UNSAFE_CLASS);
        MethodHandle getSunMiscUnsafeAnonymously = MethodHandles.privateLookupIn(
                        securityActions,
                        MethodHandles.lookup())
                .findStatic(
                        securityActions,
                        "getSunMiscUnsafeAnonymously",
                        MethodType.methodType(theUnsafeClass));
        Object unsafe = getSunMiscUnsafeAnonymously.invoke();
        MethodHandle callUnsafe = MethodHandles.privateLookupIn(theUnsafeClass, MethodHandles.lookup())
                .findVirtual(
                        theUnsafeClass,
                        "call",
                        MethodType.methodType(Object.class, String.class, Object[].class));

        Object addressSize = callUnsafe.invoke(unsafe, "addressSize", new Object[0]);

        assertThat(addressSize).isInstanceOf(Integer.class);
        assertThat((Integer) addressSize).isPositive();
    }
}
