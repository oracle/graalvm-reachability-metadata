/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.ShortByReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises JNA direct mapping, which builds the libffi call descriptors in
 * Java: {@code Native.register} resolves every parameter and return type
 * through {@code Structure$FFIType.get}, running the static initializer that
 * reflectively instantiates {@code Structure$FFIType} and reads its fields,
 * and builds a default value for every {@code NativeMapped} parameter through
 * {@code NativeMappedConverter}.
 */
class DirectMappingTest {

    @BeforeAll
    static void register() {
        Native.register(DirectMappedCLibrary.class, "c");
    }

    @Test
    void registerAndCall() {
        assertThat(DirectMappedCLibrary.atol("42")).isEqualTo(42);
        assertThat(DirectMappedCLibrary.labs(new NativeLong(-42)).longValue()).isEqualTo(42L);
    }

    @Test
    void byReferenceParameters() {
        ByteByReference byteRef = new ByteByReference();
        DirectMappedCLibrary.memcpy(byteRef, new ByteByReference((byte) 42), Byte.BYTES);
        assertThat(byteRef.getValue()).isEqualTo((byte) 42);

        ShortByReference shortRef = new ShortByReference();
        DirectMappedCLibrary.memcpy(shortRef, new ShortByReference((short) 42), Short.BYTES);
        assertThat(shortRef.getValue()).isEqualTo((short) 42);

        IntByReference intRef = new IntByReference();
        DirectMappedCLibrary.memcpy(intRef, new IntByReference(42), Integer.BYTES);
        assertThat(intRef.getValue()).isEqualTo(42);

        LongByReference longRef = new LongByReference();
        DirectMappedCLibrary.memcpy(longRef, new LongByReference(42L), Long.BYTES);
        assertThat(longRef.getValue()).isEqualTo(42L);

        FloatByReference floatRef = new FloatByReference();
        DirectMappedCLibrary.memcpy(floatRef, new FloatByReference(42f), Float.BYTES);
        assertThat(floatRef.getValue()).isEqualTo(42f);

        DoubleByReference doubleRef = new DoubleByReference();
        DirectMappedCLibrary.memcpy(doubleRef, new DoubleByReference(42d), Double.BYTES);
        assertThat(doubleRef.getValue()).isEqualTo(42d);

        NativeLongByReference nativeLongRef = new NativeLongByReference();
        DirectMappedCLibrary.memcpy(nativeLongRef, new NativeLongByReference(new NativeLong(42)), NativeLong.SIZE);
        assertThat(nativeLongRef.getValue().longValue()).isEqualTo(42L);

        PointerByReference pointerRef = new PointerByReference();
        DirectMappedCLibrary.memcpy(pointerRef, new PointerByReference(Pointer.createConstant(42)), Native.POINTER_SIZE);
        assertThat(Pointer.nativeValue(pointerRef.getValue())).isEqualTo(42L);
    }
}
