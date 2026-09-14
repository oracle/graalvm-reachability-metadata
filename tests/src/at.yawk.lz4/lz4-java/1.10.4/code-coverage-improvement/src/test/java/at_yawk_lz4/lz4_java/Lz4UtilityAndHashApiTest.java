/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.Checksum;

import org.junit.jupiter.api.Test;

import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.util.ByteBufferUtils;
import net.jpountz.util.Native;
import net.jpountz.util.SafeUtils;
import net.jpountz.util.UnsafeUtils;
import net.jpountz.util.Utils;
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

class Lz4UtilityAndHashApiTest {
    private static final byte[] DATA = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};

    @Test
    void unsafeAndSafeUtilitiesReadWritePrimitiveRepresentations() {
        byte[] bytes = new byte[32];
        UnsafeUtils.writeByte(bytes, 0, (byte) 0xA5);
        UnsafeUtils.writeByte(bytes, 1, 0xB6);
        assertThat(UnsafeUtils.readByte(bytes, 0)).isEqualTo((byte) 0xA5);
        assertThat(UnsafeUtils.readByte(bytes, 1)).isEqualTo((byte) 0xB6);
        UnsafeUtils.writeLong(bytes, 2, 0x0102030405060708L);
        assertThat(UnsafeUtils.readLong(bytes, 2)).isEqualTo(0x0102030405060708L);
        UnsafeUtils.writeInt(bytes, 10, 0x10203040);
        assertThat(UnsafeUtils.readInt(bytes, 10)).isEqualTo(0x10203040);
        UnsafeUtils.writeShort(bytes, 14, (short) 0x5060);
        assertThat(UnsafeUtils.readShort(bytes, 14)).isEqualTo((short) 0x5060);
        UnsafeUtils.writeShortLE(bytes, 16, 0x7080);
        assertThat(UnsafeUtils.readShortLE(bytes, 16)).isEqualTo(0x7080);
        bytes[18] = 8;
        bytes[19] = 7;
        bytes[20] = 6;
        bytes[21] = 5;
        bytes[22] = 4;
        bytes[23] = 3;
        bytes[24] = 2;
        bytes[25] = 1;
        assertThat(UnsafeUtils.readLongLE(bytes, 18)).isEqualTo(0x0102030405060708L);
        assertThat(UnsafeUtils.readIntLE(new byte[]{4, 3, 2, 1}, 0)).isEqualTo(0x01020304);

        int[] ints = new int[2];
        UnsafeUtils.writeInt(ints, 1, 0x12345678);
        assertThat(UnsafeUtils.readInt(ints, 1)).isEqualTo(0x12345678);
        short[] shorts = new short[2];
        UnsafeUtils.writeShort(shorts, 1, 0xFEDC);
        assertThat(UnsafeUtils.readShort(shorts, 1)).isEqualTo(0xFEDC);
        UnsafeUtils.checkRange(bytes, 0);
        UnsafeUtils.checkRange(bytes, 2, 4);
        UnsafeUtils.checkLength(0);
        assertThatThrownBy(() -> UnsafeUtils.checkRange(bytes, -1)).isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> UnsafeUtils.checkRange(bytes, 30, 3)).isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> UnsafeUtils.checkLength(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnsafeUtils.valueOf("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(UnsafeUtils.values()).isEmpty();

        int[] safeInts = new int[1];
        SafeUtils.writeInt(safeInts, 0, 42);
        assertThat(SafeUtils.readInt(safeInts, 0)).isEqualTo(42);
        assertThat(SafeUtils.readIntBE(new byte[]{1, 2, 3, 4}, 0)).isEqualTo(0x01020304);
        assertThat(SafeUtils.readLongLE(new byte[]{8, 7, 6, 5, 4, 3, 2, 1}, 0))
                .isEqualTo(0x0102030405060708L);
        assertThatThrownBy(() -> SafeUtils.valueOf("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SafeUtils.values()).isEmpty();
    }

    @Test
    void byteBufferUtilitiesPreserveOrderAndValidateBuffers() {
        ByteBuffer nativeBuffer = ByteBuffer.allocate(24).order(ByteOrder.nativeOrder());
        ByteBufferUtils.writeByte(nativeBuffer, 0, 0x7f);
        ByteBufferUtils.writeInt(nativeBuffer, 1, 0x10203040);
        ByteBufferUtils.writeLong(nativeBuffer, 5, 0x0102030405060708L);
        assertThat(ByteBufferUtils.readByte(nativeBuffer, 0)).isEqualTo((byte) 0x7f);
        assertThat(ByteBufferUtils.readInt(nativeBuffer, 1)).isEqualTo(0x10203040);
        assertThat(ByteBufferUtils.readLong(nativeBuffer, 5)).isEqualTo(0x0102030405060708L);

        ByteBuffer littleBuffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        ByteBufferUtils.writeShortLE(littleBuffer, 0, 0xBEEF);
        littleBuffer.putInt(2, 0x10203040);
        littleBuffer.putLong(6, 0x0102030405060708L);
        assertThat(ByteBufferUtils.readShortLE(littleBuffer, 0)).isEqualTo(0xBEEF);
        assertThat(ByteBufferUtils.readIntLE(littleBuffer, 2)).isEqualTo(0x10203040);
        assertThat(ByteBufferUtils.readLongLE(littleBuffer, 6)).isEqualTo(0x0102030405060708L);

        assertThat(ByteBufferUtils.inLittleEndianOrder(littleBuffer)).isSameAs(littleBuffer);
        ByteBuffer bigBuffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        assertThat(ByteBufferUtils.inLittleEndianOrder(bigBuffer).order()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
        assertThat(ByteBufferUtils.inNativeByteOrder(nativeBuffer)).isSameAs(nativeBuffer);
        assertThat(ByteBufferUtils.inNativeByteOrder(bigBuffer).order()).isEqualTo(ByteOrder.nativeOrder());
        ByteBufferUtils.checkRange(nativeBuffer, 0);
        ByteBufferUtils.checkRange(nativeBuffer, 0, 8);
        ByteBufferUtils.checkNotReadOnly(nativeBuffer);
        assertThatThrownBy(() -> ByteBufferUtils.checkRange(nativeBuffer, -1))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> ByteBufferUtils.checkRange(nativeBuffer, 20, 5))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        assertThatThrownBy(() -> ByteBufferUtils.checkNotReadOnly(nativeBuffer.asReadOnlyBuffer()))
                .isInstanceOf(java.nio.ReadOnlyBufferException.class);
        assertThatThrownBy(() -> ByteBufferUtils.valueOf("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ByteBufferUtils.values()).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation")
    void factoriesExposeImplementationsAndHashStreamingMatchesBlockHashing() {
        LZ4Factory safe = LZ4Factory.safeInstance();
        assertThat(LZ4Factory.fastestInstance()).isNotNull();
        assertThat(LZ4Factory.fastestJavaInstance()).isNotNull();
        assertThat(LZ4Factory.nativeInstance()).isNotNull();
        assertThat(LZ4Factory.nativeInsecureInstance()).isNotNull();
        assertThat(LZ4Factory.unsafeInstance()).isNotNull();
        assertThat(LZ4Factory.unsafeInsecureInstance()).isNotNull();
        assertThat(safe.highCompressor(0)).isNotNull();
        assertThat(safe.highCompressor(99)).isNotNull();
        assertThat(safe.decompressor()).isSameAs(safe.fastDecompressor());
        assertThat(safe.unknownSizeDecompressor()).isSameAs(safe.safeDecompressor());
        assertThat(safe.toString()).contains("LZ4Factory");
        LZ4Factory.main(new String[0]);

        XXHashFactory hashFactory = XXHashFactory.safeInstance();
        assertThat(XXHashFactory.fastestInstance()).isNotNull();
        assertThat(XXHashFactory.fastestJavaInstance()).isNotNull();
        assertThat(XXHashFactory.nativeInstance()).isNotNull();
        assertThat(XXHashFactory.unsafeInstance()).isNotNull();
        assertThat(hashFactory.toString()).contains("XXHashFactory");
        XXHashFactory.main(new String[0]);
        XXHash32 hash32 = hashFactory.hash32();
        XXHash64 hash64 = hashFactory.hash64();
        assertThat(hash32.toString()).contains("Hash32");
        assertThat(hash64.toString()).contains("Hash64");

        ByteBuffer buffer32 = ByteBuffer.wrap(DATA);
        int expected32 = hash32.hash(DATA, 0, DATA.length, 123);
        assertThat(hash32.hash(buffer32, 123)).isEqualTo(expected32);
        assertThat(buffer32.position()).isEqualTo(buffer32.limit());
        ByteBuffer buffer64 = ByteBuffer.wrap(DATA);
        long expected64 = hash64.hash(DATA, 0, DATA.length, 123L);
        assertThat(hash64.hash(buffer64, 123L)).isEqualTo(expected64);
        assertThat(buffer64.position()).isEqualTo(buffer64.limit());

        StreamingXXHash32 streaming32 = hashFactory.newStreamingHash32(123);
        Checksum checksum32 = streaming32.asChecksum();
        checksum32.update(DATA[0]);
        checksum32.update(DATA, 1, DATA.length - 1);
        assertThat(streaming32.getValue()).isEqualTo(expected32);
        assertThat(checksum32.toString()).contains("Streaming");
        streaming32.close();
        streaming32.reset();
        assertThat(streaming32.getValue()).isEqualTo(hash32.hash(new byte[0], 0, 0, 123));

        StreamingXXHash64 streaming64 = hashFactory.newStreamingHash64(123L);
        Checksum checksum64 = streaming64.asChecksum();
        checksum64.update(DATA[0]);
        checksum64.update(DATA, 1, DATA.length - 1);
        assertThat(streaming64.getValue()).isEqualTo(expected64);
        assertThat(checksum64.toString()).contains("Streaming");
        streaming64.close();
        streaming64.reset();
        assertThat(streaming64.getValue()).isEqualTo(hash64.hash(new byte[0], 0, 0, 123L));
    }

    @Test
    @SuppressWarnings("deprecation")
    void directHashBuffersDriveNativeAndUnsafeImplementations() {
        byte[] input = new byte[4096];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i * 17 + i / 11);
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(input.length + 6);
        direct.position(3);
        direct.put(input);
        int seed32 = 0x13579BDF;
        long seed64 = 0x123456789ABCDEFL;
        int expected32 = XXHashFactory.safeInstance().hash32().hash(input, 0, input.length, seed32);
        long expected64 = XXHashFactory.safeInstance().hash64().hash(input, 0, input.length, seed64);

        assertThat(XXHashFactory.nativeInstance().hash32().hash(direct, 3, input.length, seed32))
                .isEqualTo(expected32);
        assertThat(XXHashFactory.unsafeInstance().hash32().hash(direct, 3, input.length, seed32))
                .isEqualTo(expected32);
        assertThat(XXHashFactory.nativeInstance().hash64().hash(direct, 3, input.length, seed64))
                .isEqualTo(expected64);
        assertThat(XXHashFactory.unsafeInstance().hash64().hash(direct, 3, input.length, seed64))
                .isEqualTo(expected64);
    }

    @Test
    void nativeAndExceptionPublicTypesHaveUsefulObservableBehavior() {
        Native.load();
        assertThat(Native.isLoaded()).isTrue();
        assertThatThrownBy(() -> Native.valueOf("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Native.values()).isEmpty();
        assertThat(Utils.isUnalignedAccessAllowed()).isIn(true, false);
        assertThatThrownBy(() -> Utils.valueOf("missing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Utils.values()).isEmpty();

        Throwable cause = new IllegalStateException("cause");
        LZ4Exception noMessage = new LZ4Exception();
        LZ4Exception message = new LZ4Exception("bad compressed data");
        LZ4Exception chained = new LZ4Exception("bad block", cause);
        assertThat(noMessage.getMessage()).isNull();
        assertThat(message).hasMessage("bad compressed data");
        assertThat(chained).hasMessage("bad block").hasCause(cause);
        assertThat(Arrays.asList(noMessage, message, chained)).hasSize(3);
    }
}
