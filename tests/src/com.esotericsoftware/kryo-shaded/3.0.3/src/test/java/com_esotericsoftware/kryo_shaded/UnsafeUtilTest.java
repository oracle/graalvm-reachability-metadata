/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.util.UnsafeUtil;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class UnsafeUtilTest {
    @Test
    void createsDirectByteBufferViewOverUnsafeMemoryWhenSupportedByTheRuntime() {
        Unsafe unsafe = UnsafeUtil.unsafe();
        assertThat(UnsafeUtil.byteArrayBaseOffset).isGreaterThanOrEqualTo(0);

        if (unsafe == null) {
            assertThat(UnsafeUtil.longArrayBaseOffset).isZero();
        } else {
            long address = unsafe.allocateMemory(Integer.BYTES);
            try {
                unsafe.setMemory(address, Integer.BYTES, (byte) 0);
                ByteBuffer buffer = UnsafeUtil.getDirectBufferAt(address, Integer.BYTES);

                if (buffer == null) {
                    assertThat(buffer).isNull();
                } else {
                    assertThat(buffer.isDirect()).isTrue();
                    assertThat(buffer.capacity()).isEqualTo(Integer.BYTES);

                    buffer.put(0, (byte) 0x5A);
                    assertThat(unsafe.getByte(address)).isEqualTo((byte) 0x5A);

                    unsafe.putByte(address + 1, (byte) 0x33);
                    assertThat(buffer.get(1)).isEqualTo((byte) 0x33);
                }
            } finally {
                unsafe.freeMemory(address);
            }
        }
    }
}
