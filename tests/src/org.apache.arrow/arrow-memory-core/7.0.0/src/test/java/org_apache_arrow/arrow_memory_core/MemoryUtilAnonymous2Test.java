/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import org.apache.arrow.memory.util.MemoryUtil;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUtilAnonymous2Test {
    private static final String DIRECT_BUFFER_LOOKUP_ACTION_CLASS_NAME =
            "org.apache.arrow.memory.util.MemoryUtil$2";
    private static final String DIRECT_BUFFER_FIELD_NAME = "val$direct";

    @Test
    void runLooksUpDeclaredConstructorOnConfiguredBufferClass() throws Throwable {
        Class<?> privilegedActionClass = Class.forName(DIRECT_BUFFER_LOOKUP_ACTION_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                privilegedActionClass,
                MethodHandles.lookup()
        );
        MethodHandle actionConstructor = lookup.findConstructor(
                privilegedActionClass,
                MethodType.methodType(void.class, ByteBuffer.class)
        );
        @SuppressWarnings("unchecked")
        PrivilegedAction<Object> privilegedAction =
                (PrivilegedAction<Object>) actionConstructor.invoke((ByteBuffer) null);
        ConstructorLookupTarget lookupTarget = new ConstructorLookupTarget(0L, 0);
        replaceDirectBufferField(privilegedActionClass, privilegedAction, lookupTarget);

        Object result = AccessController.doPrivileged(privilegedAction);

        assertThat(result).isInstanceOf(Constructor.class);
        Constructor<?> constructor = (Constructor<?>) result;
        assertThat(constructor.getDeclaringClass()).isEqualTo(ConstructorLookupTarget.class);
        assertThat(constructor.getParameterTypes()).containsExactly(long.class, int.class);
    }

    private static void replaceDirectBufferField(
            Class<?> privilegedActionClass,
            PrivilegedAction<Object> privilegedAction,
            Object directBufferReplacement
    ) throws NoSuchFieldException {
        Field field = privilegedActionClass.getDeclaredField(DIRECT_BUFFER_FIELD_NAME);
        long fieldOffset = MemoryUtil.UNSAFE.objectFieldOffset(field);
        MemoryUtil.UNSAFE.putObject(privilegedAction, fieldOffset, directBufferReplacement);
    }

    public static final class ConstructorLookupTarget {
        private final long address;
        private final int capacity;

        private ConstructorLookupTarget(long address, int capacity) {
            this.address = address;
            this.capacity = capacity;
        }
    }
}
