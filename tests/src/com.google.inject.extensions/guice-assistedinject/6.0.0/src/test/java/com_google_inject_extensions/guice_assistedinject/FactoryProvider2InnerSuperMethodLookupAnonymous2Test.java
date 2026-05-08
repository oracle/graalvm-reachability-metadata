/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject_extensions.guice_assistedinject;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class FactoryProvider2InnerSuperMethodLookupAnonymous2Test {
    @Test
    void findSpecialStrategyCreatesHandleForInterfaceDefaultMethod() throws Throwable {
        Object findSpecialStrategy = findSpecialStrategy();
        MethodHandle superMethodHandle = MethodHandles.privateLookupIn(
                        findSpecialStrategy.getClass().getSuperclass(), MethodHandles.lookup())
                .findVirtual(
                        findSpecialStrategy.getClass().getSuperclass(),
                        "superMethodHandle",
                        MethodType.methodType(
                                MethodHandle.class, Method.class, MethodHandles.Lookup.class));
        Method defaultMethod = GreetingFactory.class.getMethod("greet", String.class);

        MethodHandle greetingHandle = (MethodHandle) superMethodHandle.invoke(
                findSpecialStrategy, defaultMethod, MethodHandles.lookup());
        GreetingFactory factory = name -> "hello " + name;

        assertThat((String) greetingHandle.invoke(factory, "guice")).isEqualTo("hello guice!");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object findSpecialStrategy() throws ClassNotFoundException {
        Class lookupClass = Class.forName(
                        "com.google.inject.assistedinject.FactoryProvider2$SuperMethodLookup")
                .asSubclass(Enum.class);
        return Enum.valueOf(lookupClass, "FIND_SPECIAL");
    }

    private interface GreetingFactory {
        String prefix(String name);

        default String greet(String name) {
            return prefix(name) + "!";
        }
    }
}
