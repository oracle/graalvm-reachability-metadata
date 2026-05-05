/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_lz4.lz4_java;

import net.jpountz.util.UnsafeUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnsafeUtilsTest {

    @Test
    void shouldInitializeUnsafeUtilsAndOperateOnPrimitiveArrays() {
        byte[] bytes = new byte[16];

        UnsafeUtils.checkRange(bytes, 0, bytes.length);
        UnsafeUtils.writeByte(bytes, 0, 0x12);
        UnsafeUtils.writeShortLE(bytes, 1, 0x3456);
        UnsafeUtils.writeInt(bytes, 4, 0x789ABCDE);
        UnsafeUtils.writeLong(bytes, 8, 0x0102030405060708L);

        assertThat(UnsafeUtils.readByte(bytes, 0)).isEqualTo((byte) 0x12);
        assertThat(UnsafeUtils.readShortLE(bytes, 1)).isEqualTo(0x3456);
        assertThat(UnsafeUtils.readInt(bytes, 4)).isEqualTo(0x789ABCDE);
        assertThat(UnsafeUtils.readLong(bytes, 8)).isEqualTo(0x0102030405060708L);

        int[] ints = new int[2];
        UnsafeUtils.writeInt(ints, 1, 0xCAFEBABE);
        assertThat(UnsafeUtils.readInt(ints, 1)).isEqualTo(0xCAFEBABE);

        short[] shorts = new short[2];
        UnsafeUtils.writeShort(shorts, 1, 0xBEEF);
        assertThat(UnsafeUtils.readShort(shorts, 1)).isEqualTo(0xBEEF);
    }
}
