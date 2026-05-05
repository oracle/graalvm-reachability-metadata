/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import com.diffplug.common.base.Throwables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThrowablesTest {
    @Test
    void invokeAccessibleNonThrowingMethodInvokesPublicMethod() throws Throwable {
        Method substring = MethodHandles.reflectAs(
                Method.class,
                MethodHandles.publicLookup().findVirtual(
                        String.class,
                        "substring",
                        MethodType.methodType(String.class, int.class, int.class)));
        Object result = invokeAccessibleNonThrowingMethod(substring, "durian-core", 0, 6);

        assertThat(result).isEqualTo("durian");
    }

    private static Object invokeAccessibleNonThrowingMethod(
            Method method, Object receiver, Object... params) throws Throwable {
        MethodHandle invokeAccessibleNonThrowingMethod = MethodHandles
                .privateLookupIn(Throwables.class, MethodHandles.lookup())
                .findStatic(
                        Throwables.class,
                        "invokeAccessibleNonThrowingMethod",
                        MethodType.methodType(
                                Object.class,
                                Method.class,
                                Object.class,
                                Object[].class));
        return (Object) invokeAccessibleNonThrowingMethod.invokeExact(method, receiver, params);
    }
}
