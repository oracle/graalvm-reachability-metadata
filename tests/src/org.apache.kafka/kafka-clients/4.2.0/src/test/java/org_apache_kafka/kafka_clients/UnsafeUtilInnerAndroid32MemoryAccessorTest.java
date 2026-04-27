/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class UnsafeUtilInnerAndroid32MemoryAccessorTest {
    private static final String UNSAFE_UTIL_CLASS_NAME = "org.apache.kafka.shaded.com.google.protobuf.UnsafeUtil";
    private static final String ANDROID_32_MEMORY_ACCESSOR_CLASS_NAME =
        "org.apache.kafka.shaded.com.google.protobuf.UnsafeUtil$Android32MemoryAccessor";
    private static final MethodHandle ANDROID_32_CONSTRUCTOR_HANDLE = findAndroid32ConstructorHandle();
    private static final MethodHandle GET_STATIC_OBJECT_HANDLE = findGetStaticObjectHandle();

    @Test
    void getStaticObjectReturnsValueOfPublicStaticField() throws Throwable {
        Object accessor = ANDROID_32_CONSTRUCTOR_HANDLE.invokeExact((Unsafe) null);
        Field field = StaticFieldHolder.class.getField("VALUE");

        Object resolved = GET_STATIC_OBJECT_HANDLE.invokeExact(accessor, field);

        assertThat(resolved).isSameAs(StaticFieldHolder.VALUE);
    }

    private static MethodHandle findAndroid32ConstructorHandle() {
        try {
            MethodHandles.Lookup unsafeUtilLookup = unsafeUtilLookup();
            Class<?> accessorClass = unsafeUtilLookup.findClass(ANDROID_32_MEMORY_ACCESSOR_CLASS_NAME);
            return unsafeUtilLookup.findConstructor(accessorClass, MethodType.methodType(void.class, Unsafe.class))
                .asType(MethodType.methodType(Object.class, Unsafe.class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle findGetStaticObjectHandle() {
        try {
            MethodHandles.Lookup unsafeUtilLookup = unsafeUtilLookup();
            Class<?> accessorClass = unsafeUtilLookup.findClass(ANDROID_32_MEMORY_ACCESSOR_CLASS_NAME);
            return unsafeUtilLookup
                .findVirtual(accessorClass, "getStaticObject", MethodType.methodType(Object.class, Field.class))
                .asType(MethodType.methodType(Object.class, Object.class, Field.class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandles.Lookup unsafeUtilLookup() throws ClassNotFoundException, IllegalAccessException {
        Class<?> unsafeUtilClass = Class.forName(UNSAFE_UTIL_CLASS_NAME);
        return MethodHandles.privateLookupIn(unsafeUtilClass, MethodHandles.lookup());
    }

    public static final class StaticFieldHolder {
        public static final Object VALUE = new Object();

        private StaticFieldHolder() {
        }
    }
}
