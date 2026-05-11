/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import org.apache.arrow.memory.util.MemoryUtil;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MemoryUtilTest {
    private static final String DIRECT_BUFFER_CONSTRUCTOR_FIELD_NAME = "DIRECT_BUFFER_CONSTRUCTOR";

    @Test
    void directBufferCreatesByteBufferViewForMemoryAddressWhenConstructorIsAvailable() throws Exception {
        Constructor<?> compatibleConstructor = findCompatibleDirectBufferConstructor();
        Object previousConstructor = replaceDirectBufferConstructor(compatibleConstructor);
        int capacity = 8;
        long address = MemoryUtil.UNSAFE.allocateMemory(capacity);
        try {
            MemoryUtil.UNSAFE.setMemory(address, capacity, (byte) 0);

            ByteBuffer buffer = MemoryUtil.directBuffer(address, capacity);

            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(capacity);
            assertThat(MemoryUtil.getByteBufferAddress(buffer)).isEqualTo(address);

            buffer.put(0, (byte) 42);
            assertThat(MemoryUtil.UNSAFE.getByte(address)).isEqualTo((byte) 42);
        } finally {
            MemoryUtil.UNSAFE.freeMemory(address);
            replaceDirectBufferConstructor(previousConstructor);
        }
    }

    @Test
    void directBufferRejectsNegativeCapacityBeforeInvokingConstructor() throws Exception {
        Constructor<?> compatibleConstructor = findCompatibleDirectBufferConstructor();
        Object previousConstructor = replaceDirectBufferConstructor(compatibleConstructor);
        try {
            assertThatThrownBy(() -> MemoryUtil.directBuffer(1L, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Capacity is negative");
        } finally {
            replaceDirectBufferConstructor(previousConstructor);
        }
    }

    private static Constructor<?> findCompatibleDirectBufferConstructor() throws Exception {
        Class<? extends ByteBuffer> directBufferClass = ByteBuffer.allocateDirect(0).getClass();
        try {
            Constructor<?> constructor = directBufferClass.getDeclaredConstructor(long.class, int.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            Constructor<?> constructor = directBufferClass.getDeclaredConstructor(long.class, long.class);
            constructor.setAccessible(true);
            return constructor;
        }
    }

    private static Object replaceDirectBufferConstructor(Object constructor) throws Exception {
        Field field = MemoryUtil.class.getDeclaredField(DIRECT_BUFFER_CONSTRUCTOR_FIELD_NAME);
        Object staticFieldBase = MemoryUtil.UNSAFE.staticFieldBase(field);
        long staticFieldOffset = MemoryUtil.UNSAFE.staticFieldOffset(field);
        Object previousValue = MemoryUtil.UNSAFE.getObject(staticFieldBase, staticFieldOffset);
        MemoryUtil.UNSAFE.putObjectVolatile(staticFieldBase, staticFieldOffset, constructor);
        return previousValue;
    }
}
