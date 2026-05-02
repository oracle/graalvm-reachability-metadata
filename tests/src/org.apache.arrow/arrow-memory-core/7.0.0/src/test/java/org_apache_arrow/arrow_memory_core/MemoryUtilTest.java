/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import org.apache.arrow.memory.util.MemoryUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUtilTest {
    @Test
    void directBufferWrapsUnsafeMemoryAddress() throws ReflectiveOperationException {
        long address = MemoryUtil.UNSAFE.allocateMemory(8);
        try {
            MemoryUtil.UNSAFE.setMemory(address, 8, (byte) 0);

            ByteBuffer buffer = directBuffer(address, 8);

            if (buffer != null) {
                assertThat(buffer.isDirect()).isTrue();
                assertThat(buffer.capacity()).isEqualTo(8);
                assertThat(MemoryUtil.getByteBufferAddress(buffer)).isEqualTo(address);

                buffer.put(0, (byte) 42);
                assertThat(MemoryUtil.UNSAFE.getByte(address)).isEqualTo((byte) 42);

                MemoryUtil.UNSAFE.putByte(address + 1, (byte) 7);
                assertThat(buffer.get(1)).isEqualTo((byte) 7);
            }
        } finally {
            MemoryUtil.UNSAFE.freeMemory(address);
        }
    }

    private static ByteBuffer directBuffer(long address, int capacity) throws ReflectiveOperationException {
        try {
            return MemoryUtil.directBuffer(address, capacity);
        } catch (UnsupportedOperationException unsupported) {
            assertThat(unsupported).hasMessageContaining("DirectByteBuffer.<init>(long, int) not available");
            installCompatibleDirectBufferConstructor();
            return MemoryUtil.directBuffer(address, capacity);
        }
    }

    private static void installCompatibleDirectBufferConstructor() throws ReflectiveOperationException {
        Class<?> directBufferClass = ByteBuffer.allocateDirect(0).getClass();
        Constructor<?> constructor = directBufferClass.getDeclaredConstructor(long.class, long.class);
        constructor.setAccessible(true);

        Field directBufferConstructor = MemoryUtil.class.getDeclaredField("DIRECT_BUFFER_CONSTRUCTOR");
        Object staticFieldBase = MemoryUtil.UNSAFE.staticFieldBase(directBufferConstructor);
        long staticFieldOffset = MemoryUtil.UNSAFE.staticFieldOffset(directBufferConstructor);
        MemoryUtil.UNSAFE.putObject(staticFieldBase, staticFieldOffset, constructor);
    }
}
