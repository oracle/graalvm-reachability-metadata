/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static java.lang.invoke.MethodType.methodType;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

public class UnsafeUtilInnerAndroid32MemoryAccessorTest {
    private static final String ANDROID_32_MEMORY_ACCESSOR_CLASS_NAME =
            "com.google.protobuf.UnsafeUtil$Android32MemoryAccessor";
    public static final Object STATIC_VALUE = new Object();

    @Test
    void android32MemoryAccessorReadsStaticFieldValue() throws Throwable {
        ClassLoader protobufClassLoader = ByteString.class.getClassLoader();
        Class<?> android32MemoryAccessorClass = Class.forName(
                ANDROID_32_MEMORY_ACCESSOR_CLASS_NAME,
                true,
                protobufClassLoader
        );
        MethodHandles.Lookup android32MemoryAccessorLookup = MethodHandles.privateLookupIn(
                android32MemoryAccessorClass,
                MethodHandles.lookup()
        );
        MethodHandle constructor = android32MemoryAccessorLookup.findConstructor(
                android32MemoryAccessorClass,
                methodType(void.class, sun.misc.Unsafe.class)
        );
        Object memoryAccessor = constructor.invoke((sun.misc.Unsafe) null);
        MethodHandle getStaticObject = android32MemoryAccessorLookup.findVirtual(
                android32MemoryAccessorClass,
                "getStaticObject",
                methodType(Object.class, Field.class)
        );

        Object value = getStaticObject.invoke(
                memoryAccessor,
                UnsafeUtilInnerAndroid32MemoryAccessorTest.class.getDeclaredField("STATIC_VALUE")
        );

        assertThat(value).isSameAs(STATIC_VALUE);
    }
}
