/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.WString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryPointerCoverageTest {
    private static final boolean JVM_ONLY = System.getProperty("org.graalvm.nativeimage.imagecode") == null;

    @Test
    void typedMemoryRoundTrip() {
        Memory memory = new Memory(1024);
        memory.setByte(0, (byte) 7);
        memory.setChar(8, 'J');
        memory.setShort(16, (short) 32000);
        memory.setInt(24, 123456);
        memory.setLong(32, 9876543210L);
        memory.setFloat(40, 1.5f);
        memory.setDouble(48, 2.5d);
        memory.setNativeLong(64, new NativeLong(77));
        memory.setString(80, "cafe");
        memory.setString(96, "cafe", "UTF-8");
        if (JVM_ONLY) {
            memory.setWideString(112, "wide");
        }

        assertThat(memory.getByte(0)).isEqualTo((byte) 7);
        assertThat(memory.getChar(8)).isEqualTo('J');
        assertThat(memory.getShort(16)).isEqualTo((short) 32000);
        assertThat(memory.getInt(24)).isEqualTo(123456);
        assertThat(memory.getLong(32)).isEqualTo(9876543210L);
        assertThat(memory.getFloat(40)).isEqualTo(1.5f);
        assertThat(memory.getDouble(48)).isEqualTo(2.5d);
        assertThat(memory.getNativeLong(64)).isEqualTo(new NativeLong(77));
        assertThat(memory.getString(80)).isEqualTo("cafe");
        assertThat(memory.getString(96, "UTF-8")).isEqualTo("cafe");
        if (JVM_ONLY) {
            assertThat(memory.getWideString(112)).isEqualTo("wide");
            Pointer rawPointer = new Pointer(Pointer.nativeValue(memory));
            rawPointer.setWideString(128, "raw wide");
            assertThat(rawPointer.getWideString(128)).isEqualTo("raw wide");
        }
        assertThat(memory.size()).isEqualTo(1024);
        assertThat(memory.valid()).isTrue();
        assertThat(memory.toString()).contains("1024 bytes");
        assertThat(memory.dump()).contains("00000000");

        ByteBuffer buffer = memory.getByteBuffer(24, 16);
        assertThat(buffer.isDirect()).isTrue();
        assertThat(buffer.order()).isEqualTo(ByteOrder.nativeOrder());
        assertThat(Native.getDirectBufferPointer(buffer)).isNotNull();
    }

    @Test
    void arrayAndPointerOperations() {
        Memory memory = new Memory(1024);
        byte[] bytes = {1, 2, 3};
        short[] shorts = {4, 5};
        char[] chars = {'a', 'b'};
        int[] ints = {6, 7};
        long[] longs = {8L, 9L};
        float[] floats = {1.25f, 2.5f};
        double[] doubles = {3.75d, 4.5d};
        memory.write(0, bytes, 0, bytes.length);
        memory.write(8, shorts, 0, shorts.length);
        memory.write(16, chars, 0, chars.length);
        memory.write(32, ints, 0, ints.length);
        memory.write(48, longs, 0, longs.length);
        memory.write(72, floats, 0, floats.length);
        memory.write(88, doubles, 0, doubles.length);

        assertThat(memory.getByteArray(0, 3)).containsExactly(bytes);
        assertThat(memory.getShortArray(8, 2)).containsExactly(shorts);
        assertThat(memory.getCharArray(16, 2)).containsExactly(chars);
        assertThat(memory.getIntArray(32, 2)).containsExactly(ints);
        assertThat(memory.getLongArray(48, 2)).containsExactly(longs);
        assertThat(memory.getFloatArray(72, 2)).containsExactly(floats);
        assertThat(memory.getDoubleArray(88, 2)).containsExactly(doubles);

        Memory pointed = new Memory(8);
        Pointer constant = Pointer.createConstant(1234L);
        Pointer intConstant = Pointer.createConstant(1234);
        assertThat(Pointer.nativeValue(intConstant)).isEqualTo(1234L);
        Pointer[] pointers = {pointed, constant};
        memory.write(112, pointers, 0, pointers.length);
        assertThat(memory.getPointerArray(112, 2)).containsExactly(pointers);
        memory.setPointer(128, pointed);
        memory.setPointer(128 + Native.POINTER_SIZE, null);
        assertThat(memory.getPointerArray(128)).containsExactly(pointed);
        assertThat(memory.getPointer(128)).isEqualTo(pointed);
        memory.setPointer(128, memory);
        assertThat(memory.indexOf(0, (byte) 3)).isEqualTo(2);

        Pointer shared = memory.share(32);
        assertThat(shared).isNotSameAs(memory);
        assertThat(shared.getInt(0)).isEqualTo(6);
        assertThat(memory.getPointer(128)).isInstanceOf(Memory.class);
        assertThat(Pointer.nativeValue(constant)).isEqualTo(1234L);
        Pointer base = new Pointer(0x1000L);
        Pointer offset = base.share(0x20L);
        assertThat(Pointer.nativeValue(offset)).isEqualTo(0x1020L);
        assertThat(base.share(0L)).isSameAs(base);
        Pointer mutable = new Pointer(0);
        Pointer.nativeValue(mutable, 0x5678L);
        assertThat(Pointer.nativeValue(mutable)).isEqualTo(0x5678L);
        assertThat(new Pointer(7)).isEqualTo(new Pointer(7));
        assertThat(new Pointer(7)).isNotEqualTo(new Pointer(8));
        assertThat(new Pointer(7).hashCode()).isEqualTo(new Pointer(7).hashCode());
    }

    @Test
    void stringPointerArraysAndSharedMemory() {
        StringArray narrowPointers = new StringArray(new String[]{"first", "second"});
        Pointer pointers = narrowPointers;
        assertThat(pointers.getStringArray(0)).containsExactly("first", "second");
        assertThat(pointers.getStringArray(0, "UTF-8")).containsExactly("first", "second");
        assertThat(pointers.getStringArray(0, 2)).containsExactly("first", "second");
        assertThat(pointers.getStringArray(0, 2, "UTF-8")).containsExactly("first", "second");
        Pointer opaque = Pointer.createConstant(0x1234L);
        assertThatThrownBy(() -> opaque.getWideString(0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new WideOpaqueArrayPointer().getStringArray(0, 1, "--WIDE-STRING--"))
                .isInstanceOf(UnsupportedOperationException.class);

        StringArray narrow = new StringArray(new String[]{"one", "two"});
        StringArray explicitEncoding = new StringArray(new String[]{"one", "two"}, "UTF-8");
        narrow.read();
        explicitEncoding.read();
        assertThat(narrow.toString()).contains("one", "two");
        assertThat(explicitEncoding.toString()).contains("one", "two");
        if (JVM_ONLY) {
            StringArray wide = new StringArray(new WString[]{new WString("one"), new WString("two")});
            wide.read();
            assertThat(wide.toString()).contains("one", "two");
        }
        Memory source = new Memory(32);
        source.setInt(4, 42);
        Memory view = (Memory) source.share(4, 8);
        assertThat(view.size()).isEqualTo(8);
        assertThat(view.getInt(0)).isEqualTo(42);
        assertThat(view.share(0)).isNotSameAs(view);
        assertThat(source.align(8)).isInstanceOf(Memory.class);
    }

    @Test
    void memoryLifecycle() {
        Memory memory = new Memory(8);
        Memory shared = (Memory) memory.share(0, 4);
        Pointer pointerView = memory;
        pointerView.clear(8);
        assertThat(memory.getByteArray(0, 8)).containsOnly((byte) 0);
        assertThat(new PointerTypeForTest(shared).toString()).contains("4 bytes");
        Memory.purge();
        Memory.disposeAll();
        assertThat(memory.valid()).isFalse();
    }

    @Test
    void opaquePointerEntriesRejectNativeAccess() {
        Pointer opaque = Pointer.createConstant(0x1234L);
        assertThatThrownBy(() -> opaque.dump(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getByte(0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getInt(0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getNativeLong(0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getPointerArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getString(0)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getStringArray(0, 1, "--WIDE-STRING--"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getWideStringArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getByteArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getCharArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getDoubleArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getFloatArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getIntArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getLongArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.getShortArray(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.clear(1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setNativeLong(0, new NativeLong(1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setByte(0, (byte) 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setInt(0, 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setChar(0, 'x')).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setString(0, "x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setString(0, "x", "UTF-8"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setString(0, new com.sun.jna.WString("x")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.setPointer(0, Pointer.NULL)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new byte[]{1}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new char[]{'x'}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new short[]{1}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new int[]{1}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new long[]{1}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new float[]{1}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new double[]{1}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.write(0, new Pointer[]{Pointer.NULL}, 0, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> opaque.share(1)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class PointerTypeForTest extends com.sun.jna.PointerType {
        PointerTypeForTest(Pointer pointer) {
            super(pointer);
        }
    }

    private static final class WideOpaqueArrayPointer extends Pointer {
        WideOpaqueArrayPointer() {
            super(0);
        }

        @Override
        public Pointer getPointer(long offset) {
            return Pointer.createConstant(0x1234L);
        }
    }
}
