/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.util.UnsafeUtil;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnsafeUtilTest {
    private static final int MEMORY_SIZE = 8;

    @Test
    void initializesUnsafeSupportAndSortsFieldsByMemoryOffset() {
        Unsafe unsafe = UnsafeUtil.unsafe();
        List<Field> fields = Arrays.asList(FieldLayout.class.getDeclaredFields());

        if (unsafe == null) {
            assertThat(UnsafeUtil.byteArrayBaseOffset).isZero();
            assertThat(UnsafeUtil.intArrayBaseOffset).isZero();
        } else {
            Field[] sortedFields = UnsafeUtil.sortFieldsByOffset(fields);

            assertThat(sortedFields).containsExactlyInAnyOrderElementsOf(fields);
            assertThat(UnsafeUtil.byteArrayBaseOffset).isEqualTo(unsafe.arrayBaseOffset(byte[].class));
            assertThat(UnsafeUtil.intArrayBaseOffset).isEqualTo(unsafe.arrayBaseOffset(int[].class));
            assertThat(sortedFields).isSortedAccordingTo((left, right) -> Long.compare(
                    unsafe.objectFieldOffset(left), unsafe.objectFieldOffset(right)));
        }
    }

    @Test
    void createsDirectBufferViewForUnsafeMemoryWhenSupported() {
        Unsafe unsafe = UnsafeUtil.unsafe();
        long address = 0L;
        boolean allocated = false;
        int size = unsafe == null ? 0 : MEMORY_SIZE;

        try {
            if (unsafe != null) {
                address = unsafe.allocateMemory(MEMORY_SIZE);
                allocated = true;
                unsafe.setMemory(address, MEMORY_SIZE, (byte) 0);
            }

            ByteBuffer buffer = UnsafeUtil.getDirectBufferAt(address, size);

            if (buffer == null) {
                assertThat(buffer).isNull();
            } else {
                assertThat(buffer.isDirect()).isTrue();
                assertThat(buffer.capacity()).isEqualTo(size);
                if (unsafe != null) {
                    buffer.put(0, (byte) 0x2A);
                    assertThat(unsafe.getByte(address)).isEqualTo((byte) 0x2A);
                }
            }
        } finally {
            if (allocated) {
                unsafe.freeMemory(address);
            }
        }
    }

    @Test
    void reportsAllocationFailureFromCachedDirectBufferConstructor() throws Exception {
        Field directByteBufferConstructor = UnsafeUtil.class.getDeclaredField("directByteBufferConstr");
        directByteBufferConstructor.setAccessible(true);
        Object originalConstructor = directByteBufferConstructor.get(null);
        Constructor<ConstructorProbe> probeConstructor = ConstructorProbe.class.getConstructor(
                long.class, int.class, Object.class);

        try {
            directByteBufferConstructor.set(null, probeConstructor);

            assertThatThrownBy(() -> UnsafeUtil.getDirectBufferAt(1L, 2))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Cannot allocate ByteBuffer");
        } finally {
            directByteBufferConstructor.set(null, originalConstructor);
        }
    }

    public static class ConstructorProbe {
        private final long address;
        private final int size;
        private final Object owner;

        public ConstructorProbe(long address, int size, Object owner) {
            this.address = address;
            this.size = size;
            this.owner = owner;
        }
    }

    public static class FieldLayout {
        private byte first;
        private long second;
        private int third;
        private Object fourth;
    }
}
