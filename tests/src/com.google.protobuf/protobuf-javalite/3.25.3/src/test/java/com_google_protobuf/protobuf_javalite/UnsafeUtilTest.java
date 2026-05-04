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
import org.junit.jupiter.api.Test;

public class UnsafeUtilTest {
    private static final String UNSAFE_UTIL_CLASS_NAME = "com.google.protobuf.UnsafeUtil";

    @Test
    void initializesAndroidMemoryChecksAndAllocatesInstanceWithoutConstructor() throws Throwable {
        Class<?> unsafeUtilClass = Class.forName(
                UNSAFE_UTIL_CLASS_NAME,
                true,
                ByteString.class.getClassLoader()
        );
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                unsafeUtilClass,
                MethodHandles.lookup()
        );
        MethodHandle allocateInstance = lookup.findStatic(
                unsafeUtilClass,
                "allocateInstance",
                methodType(Object.class, Class.class)
        );

        ConstructorGuardedType instance = (ConstructorGuardedType) allocateInstance.invoke(
                ConstructorGuardedType.class
        );

        assertThat(instance.constructorValue()).isZero();
    }

    public static final class ConstructorGuardedType {
        private final int constructorValue;

        private ConstructorGuardedType() {
            constructorValue = 42;
            throw new AssertionError("Unsafe allocation must not invoke constructors");
        }

        int constructorValue() {
            return constructorValue;
        }
    }
}
