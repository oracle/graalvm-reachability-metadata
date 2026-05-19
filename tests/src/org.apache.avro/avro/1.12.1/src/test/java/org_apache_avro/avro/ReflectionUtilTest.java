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
import java.util.function.Supplier;

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
    @SuppressWarnings("unchecked")
    void createsSupplierBackedByPublicNoArgumentConstructor() throws Throwable {
        MethodHandle getConstructorAsSupplier = privateReflectionUtilLookup().findStatic(ReflectionUtil.class,
                "getConstructorAsSupplier", MethodType.methodType(Supplier.class, Class.class));

        Supplier<ConstructedWithNoArgs> supplier = (Supplier<ConstructedWithNoArgs>) getConstructorAsSupplier
                .invoke(ConstructedWithNoArgs.class);

        assertThat(supplier.get()).isInstanceOf(ConstructedWithNoArgs.class);
    }

    @Test
    void createsFunctionBackedByPublicOneArgumentConstructor() {
        Function<String, ConstructedFromString> constructor = ReflectionUtil.getConstructorAsFunction(String.class,
                ConstructedFromString.class);

        ConstructedFromString constructed = constructor.apply("created by method handle");

        assertThat(constructed.value).isEqualTo("created by method handle");
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

    public static class ConstructedFromString {
        private final String value;

        public ConstructedFromString(String value) {
            this.value = value;
        }
    }

    public static class PrivateStringConstructor {
        private PrivateStringConstructor(String value) {
        }
    }
}
