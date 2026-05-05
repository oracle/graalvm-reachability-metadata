/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.reflect.LookupFactory;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

public class LookupFactoryTest {

    @Test
    void invokesConfiguredConstructorWhenPrivateLookupInIsUnavailable() throws Throwable {
        Field privateLookupInMethodField = lookupFactoryField("privateLookupInMethod");
        Field java8LookupConstructorField = lookupFactoryField("java8LookupConstructor");
        Object originalPrivateLookupInMethod = privateLookupInMethodField.get(null);
        Object originalJava8LookupConstructor = java8LookupConstructorField.get(null);
        Constructor<LegacyLookupStandIn> legacyConstructor = LegacyLookupStandIn.class.getConstructor(
                Class.class,
                int.class);
        installLookupReturningConstructorAccessor(legacyConstructor);

        try {
            privateLookupInMethodField.set(null, null);
            java8LookupConstructorField.set(null, legacyConstructor);

            MethodHandles.Lookup lookup = LookupFactory.lookup(LegacyLookupSubject.class);
            MethodHandle secretGetter = lookup.findGetter(LegacyLookupSubject.class, "secret", String.class);

            assertThat(secretGetter.invoke(new LegacyLookupSubject())).isEqualTo("legacy lookup");
        } finally {
            privateLookupInMethodField.set(null, originalPrivateLookupInMethod);
            java8LookupConstructorField.set(null, originalJava8LookupConstructor);
        }
    }

    private static Field lookupFactoryField(String name) throws NoSuchFieldException {
        Field field = LookupFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void installLookupReturningConstructorAccessor(Constructor<?> constructor) throws Exception {
        Class<?> constructorAccessorType = Class.forName("jdk.internal.reflect.ConstructorAccessor");
        Object constructorAccessor = Proxy.newProxyInstance(
                LookupFactoryTest.class.getClassLoader(),
                new Class<?>[]{constructorAccessorType},
                (proxy, method, args) -> {
                    Object[] constructorArguments = (Object[]) args[0];
                    Class<?> callerClass = (Class<?>) constructorArguments[0];
                    return MethodHandles.privateLookupIn(callerClass, MethodHandles.lookup());
                });
        Method setConstructorAccessor = Constructor.class.getDeclaredMethod(
                "setConstructorAccessor",
                constructorAccessorType);
        setConstructorAccessor.setAccessible(true);
        setConstructorAccessor.invoke(constructor, constructorAccessor);
    }

    public static class LegacyLookupSubject {
        private final String secret = "legacy lookup";
    }

    public static class LegacyLookupStandIn {
        public LegacyLookupStandIn(Class<?> callerClass, int allowedModes) {
        }
    }
}
