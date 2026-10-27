/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;

import org.apache.avro.reflect.ReflectionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilTest {
    @Test
    void createsInstanceUsingUtilityClassLoaderPath() throws Throwable {
        MethodHandle load = privateReflectionUtilLookup().findStatic(ReflectionUtil.class, "load",
                MethodType.methodType(Object.class, String.class, Class.class));

        Object loaded = load.invoke(ConstructedWithNoArgs.class.getName(), ConstructedWithNoArgs.class);

        assertThat(loaded).isInstanceOf(ConstructedWithNoArgs.class);
    }

    @Test
    void returnsNullWhenOneArgumentConstructorIsNotAccessible() {
        Function<String, PrivateStringConstructor> constructor = ReflectionUtil.getConstructorAsFunction(String.class,
                PrivateStringConstructor.class);

        assertThat(constructor).isNull();
    }

    private static MethodHandles.Lookup privateReflectionUtilLookup() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(ReflectionUtil.class, MethodHandles.lookup());
    }

    public static class ConstructedWithNoArgs {
        public ConstructedWithNoArgs() {
        }
    }

    public static class PrivateStringConstructor {
        private PrivateStringConstructor(String value) {
        }
    }
}
