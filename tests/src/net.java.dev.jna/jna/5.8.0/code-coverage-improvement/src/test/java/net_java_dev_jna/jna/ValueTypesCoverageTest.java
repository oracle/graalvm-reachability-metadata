/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.IntegerType;
import com.sun.jna.NativeLong;
import com.sun.jna.NativeMappedConverter;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.WString;
import com.sun.jna.ptr.ByReference;
import com.sun.jna.ptr.ByteByReference;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.ptr.ShortByReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTypesCoverageTest {
    public static final class TestInteger extends IntegerType {
        public TestInteger() {
            super(4);
        }

        public TestInteger(boolean unsigned) {
            super(4, unsigned);
        }

        public TestInteger(long value) {
            super(4, value);
        }
    }

    public static final class TestPointer extends PointerType {
        public TestPointer() {
            super();
        }
    }

    @Test
    void integerTypeBehavesLikeANumber() {
        TestInteger zero = new TestInteger();
        TestInteger unsigned = new TestInteger(true);
        TestInteger value = new TestInteger(42L);
        assertThat(zero.intValue()).isZero();
        assertThat(unsigned.longValue()).isZero();
        assertThat(value.intValue()).isEqualTo(42);
        assertThat(value.floatValue()).isEqualTo(42.0f);
        assertThat(value.doubleValue()).isEqualTo(42.0d);
        assertThat(value.toNative()).isEqualTo(42);
        assertThat(value.toString()).isEqualTo("42");
        assertThat(value.hashCode()).isEqualTo(new TestInteger(42L).hashCode());
        assertThat(value).isEqualTo(new TestInteger(42L));
        assertThat(value).isNotEqualTo(new TestInteger(41L));
        assertThat(IntegerType.compare(value, new TestInteger(43L))).isNegative();
        assertThat(IntegerType.compare(value, (IntegerType) null)).isNegative();
        assertThat(IntegerType.compare((IntegerType) null, value)).isPositive();
        assertThat(IntegerType.compare(value, 42L)).isZero();
        assertThat(IntegerType.compare((IntegerType) null, 42L)).isPositive();
        assertThat(IntegerType.compare(1L, 2L)).isNegative();
        assertThat(IntegerType.compare(2L, 1L)).isPositive();
        assertThat(IntegerType.compare(2L, 2L)).isZero();

        Object converted = value.fromNative(17, null);
        assertThat(converted).isEqualTo(new TestInteger(17L));
    }

    @Test
    void pointerTypesAndReferencesRoundTripValues() {
        TestPointer pointerType = new TestPointer();
        Pointer pointer = new Pointer(0x1234);
        pointerType.setPointer(pointer);
        assertThat(pointerType.getPointer()).isEqualTo(pointer);
        assertThat(pointerType.toNative()).isEqualTo(pointer);
        assertThat(pointerType.nativeType()).isEqualTo(Pointer.class);
        assertThat((Object) pointerType.fromNative(pointer, null)).isEqualTo(pointerType);
        assertThat(pointerType.toString()).contains("1234");
        assertThat(pointerType.hashCode()).isEqualTo(pointer.hashCode());
        assertThat(pointerType).isEqualTo(new TestPointerWithPointer(pointer));

        ByteByReference byteReference = new ByteByReference();
        byteReference.setValue((byte) 8);
        assertThat(byteReference.getValue()).isEqualTo((byte) 8);
        assertThat(byteReference.toString()).contains("8");
        ByteByReference initializedByte = new ByteByReference((byte) 9);
        assertThat(initializedByte.getValue()).isEqualTo((byte) 9);

        ShortByReference shortReference = new ShortByReference();
        shortReference.setValue((short) 10);
        assertThat(shortReference.getValue()).isEqualTo((short) 10);
        assertThat(shortReference.toString()).contains("10");
        assertThat(new ShortByReference((short) 11).getValue()).isEqualTo((short) 11);

        IntByReference intReference = new IntByReference();
        intReference.setValue(12);
        assertThat(intReference.getValue()).isEqualTo(12);
        assertThat(intReference.toString()).contains("12");
        assertThat(new IntByReference(13).getValue()).isEqualTo(13);

        LongByReference longReference = new LongByReference();
        longReference.setValue(14L);
        assertThat(longReference.getValue()).isEqualTo(14L);
        assertThat(longReference.toString()).contains("14");
        assertThat(new LongByReference(15L).getValue()).isEqualTo(15L);

        FloatByReference floatReference = new FloatByReference();
        floatReference.setValue(1.5f);
        assertThat(floatReference.getValue()).isEqualTo(1.5f);
        assertThat(floatReference.toString()).contains("1.5");
        assertThat(new FloatByReference(2.5f).getValue()).isEqualTo(2.5f);

        DoubleByReference doubleReference = new DoubleByReference();
        doubleReference.setValue(3.5d);
        assertThat(doubleReference.getValue()).isEqualTo(3.5d);
        assertThat(doubleReference.toString()).contains("3.5");
        assertThat(new DoubleByReference(4.5d).getValue()).isEqualTo(4.5d);

        NativeLongByReference nativeLongReference = new NativeLongByReference();
        nativeLongReference.setValue(new NativeLong(16));
        assertThat(nativeLongReference.getValue()).isEqualTo(new NativeLong(16));
        assertThat(nativeLongReference.toString()).contains("16");
        assertThat(new NativeLongByReference(new NativeLong(17)).getValue()).isEqualTo(new NativeLong(17));

        GenericReference genericReference = new GenericReference();
        genericReference.getPointer().setInt(0, 19);
        assertThat(genericReference.toString()).contains("19");

        PointerByReference pointerReference = new PointerByReference();
        pointerReference.setValue(pointer);
        assertThat(pointerReference.getValue()).isEqualTo(pointer);
        assertThat(new PointerByReference(pointer).getValue()).isEqualTo(pointer);
    }

    public static final class GenericReference extends ByReference {
        GenericReference() {
            super(4);
        }

        public Integer getValue() {
            return getPointer().getInt(0);
        }
    }

    private static final class TestPointerWithPointer extends PointerType {
        TestPointerWithPointer(Pointer pointer) {
            super(pointer);
        }
    }

    @Test
    void opaqueReferencesRejectNativeAccess() {
        Pointer opaque = Pointer.createConstant(0x5678L);

        DoubleByReference doubleReference = new DoubleByReference();
        doubleReference.setPointer(opaque);
        assertThatThrownBy(doubleReference::getValue).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> doubleReference.setValue(1.0d)).isInstanceOf(UnsupportedOperationException.class);

        FloatByReference floatReference = new FloatByReference();
        floatReference.setPointer(opaque);
        assertThatThrownBy(floatReference::getValue).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> floatReference.setValue(1.0f)).isInstanceOf(UnsupportedOperationException.class);

        ShortByReference shortReference = new ShortByReference();
        shortReference.setPointer(opaque);
        assertThatThrownBy(shortReference::getValue).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> shortReference.setValue((short) 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stringsExceptionsAndNativeMappedConversion() {
        WString string = new WString("hello");
        assertThat(string.length()).isEqualTo(5);
        assertThat(string.charAt(1)).isEqualTo('e');
        assertThat(string.subSequence(1, 4).toString()).isEqualTo("ell");
        assertThat(string.compareTo(new WString("world"))).isNegative();
        assertThat((Object) string).isEqualTo(new WString("hello"));
        assertThat(string.hashCode()).isEqualTo(new WString("hello").hashCode());
        assertThat(string.toString()).isEqualTo("hello");

        assertThat(new com.sun.jna.LastErrorException(12).getErrorCode()).isEqualTo(12);
        assertThat(new com.sun.jna.LastErrorException("[34] failed").getErrorCode()).isEqualTo(34);
        assertThat(new com.sun.jna.LastErrorException("not a number").getErrorCode()).isEqualTo(-1);

        NativeMappedConverter converter = NativeMappedConverter.getInstance(TestInteger.class);
        assertThat(converter.toNative(new TestInteger(18), null)).isEqualTo(18);
        assertThat(converter.nativeType()).isEqualTo(Integer.class);
        assertThat(converter.defaultValue()).isInstanceOf(TestInteger.class);
    }
}
