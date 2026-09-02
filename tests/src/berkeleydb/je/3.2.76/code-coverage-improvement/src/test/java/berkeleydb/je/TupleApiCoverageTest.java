/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.ByteArrayBinding;
import com.sleepycat.bind.RecordNumberBinding;
import com.sleepycat.bind.tuple.BigIntegerBinding;
import com.sleepycat.bind.tuple.BooleanBinding;
import com.sleepycat.bind.tuple.ByteBinding;
import com.sleepycat.bind.tuple.CharacterBinding;
import com.sleepycat.bind.tuple.DoubleBinding;
import com.sleepycat.bind.tuple.FloatBinding;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.bind.tuple.ShortBinding;
import com.sleepycat.bind.tuple.SortedDoubleBinding;
import com.sleepycat.bind.tuple.SortedFloatBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleBase;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleInputBinding;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.je.DatabaseEntry;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class TupleApiCoverageTest {

    @Test
    void tupleOutputAndInputPreserveValuesAndLengths() throws Exception {
        TupleOutput output = new TupleOutput();
        output.writeBytes("abc").writeChars("XY").writeString("hello");
        output.writeFloat(1.25f).writeDouble(2.5d);
        output.writeBigInteger(new BigInteger("12345678901234567890"));
        output.writePackedInt(300);
        output.writeBytes(new char[] {'d', 'e'});
        output.writeChars(new char[] {'Z'});
        output.writeString(new char[] {'q', 'r'});
        output.write(new byte[] {1, 2, 3});

        TupleInput input = new TupleInput(output);
        assertThat(input.readBytes(3)).isEqualTo("abc");
        assertThat(input.readChars(2)).isEqualTo("XY");
        assertThat(input.readString()).isEqualTo("hello");
        assertThat(input.readFloat()).isEqualTo(1.25f);
        assertThat(input.readDouble()).isEqualTo(2.5d);
        assertThat(input.readBigInteger()).isEqualTo(new BigInteger("12345678901234567890"));
        assertThat(input.getBigIntegerByteLength()).isPositive();
        assertThat(input.readPackedInt()).isEqualTo(300);
        assertThat(input.getPackedIntByteLength()).isPositive();
        char[] bytes = new char[2];
        input.readBytes(bytes);
        assertThat(bytes).containsExactly('d', 'e');
        char[] chars = new char[1];
        input.readChars(chars);
        assertThat(chars).containsExactly('Z');
        char[] string = new char[2];
        input.readString(string);
        assertThat(string).containsExactly('q', 'r');
        assertThat(input.available()).isEqualTo(3);
    }

    @Test
    void byteArrayInputDecodesFixedLengthStrings() {
        TupleOutput output = new TupleOutput();
        output.writeString(new char[] {'o', 'k'});
        TupleInput input = new TupleInput(output.toByteArray());
        assertThat(input.readString(2)).isEqualTo("ok");
    }

    @Test
    void tupleFactoriesAndPrimitiveBindingsRoundTrip() {
        TupleBase base = new TupleBase();
        base.setTupleBufferSize(64);
        assertThat(base.getTupleBufferSize()).isEqualTo(64);
        TupleOutput output = TupleBase.newOutput();
        output.writeInt(42);
        DatabaseEntry entry = new DatabaseEntry();
        TupleBase.inputToEntry(new TupleInput(output), entry);
        assertThat(TupleBase.entryToInput(entry).readInt()).isEqualTo(42);
        assertThat(TupleBase.newOutput(new byte[8])).isNotNull();

        assertThat(roundTrip(new BooleanBinding(), true)).isEqualTo(true);
        assertThat(roundTrip(new ByteBinding(), (byte) -3)).isEqualTo((byte) -3);
        assertThat(roundTrip(new CharacterBinding(), 'x')).isEqualTo('x');
        assertThat(roundTrip(new DoubleBinding(), 4.5d)).isEqualTo(4.5d);
        assertThat(roundTrip(new FloatBinding(), 4.5f)).isEqualTo(4.5f);
        assertThat(roundTrip(new IntegerBinding(), 17)).isEqualTo(17);
        assertThat(roundTrip(new LongBinding(), 19L)).isEqualTo(19L);
        assertThat(roundTrip(new ShortBinding(), (short) 23)).isEqualTo((short) 23);
        assertThat(roundTrip(new StringBinding(), "bound")).isEqualTo("bound");
        assertThat(roundTrip(new BigIntegerBinding(), BigInteger.TEN)).isEqualTo(BigInteger.TEN);
        assertThat(roundTrip(new SortedDoubleBinding(), 2.25d)).isEqualTo(2.25d);
        assertThat(roundTrip(new SortedFloatBinding(), 2.25f)).isEqualTo(2.25f);

        assertThat(TupleBinding.getPrimitiveBinding(Integer.TYPE)).isNotNull();
        DatabaseEntry binary = new DatabaseEntry();
        byte[] bytes = {4, 5, 6};
        new ByteArrayBinding().objectToEntry(bytes, binary);
        assertThat((byte[]) new ByteArrayBinding().entryToObject(binary)).containsExactly(bytes);
        DatabaseEntry number = new DatabaseEntry();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> RecordNumberBinding.recordNumberToEntry(7L, number))
                .isInstanceOf(UnsupportedOperationException.class);
        RecordNumberBinding binding = new RecordNumberBinding();
        DatabaseEntry record = new DatabaseEntry();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> binding.objectToEntry(12L, record))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> binding.entryToRecordNumber(record))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> binding.entryToObject(record))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(binding).isNotNull();

        TupleInputBinding inputBinding = new TupleInputBinding();
        DatabaseEntry tupleEntry = new DatabaseEntry();
        inputBinding.objectToEntry(new TupleInput(new TupleOutput()), tupleEntry);
        assertThat(inputBinding.entryToObject(tupleEntry)).isInstanceOf(TupleInput.class);
        assertThat(TupleOutput.getBigIntegerByteLength(BigInteger.TEN)).isPositive();
    }

    @Test
    void staticPrimitiveEntryConversionsUsePublicDatabaseEntryContract() {
        DatabaseEntry entry = new DatabaseEntry();
        BooleanBinding.booleanToEntry(true, entry);
        assertThat(BooleanBinding.entryToBoolean(entry)).isTrue();
        ByteBinding.byteToEntry((byte) 8, entry);
        assertThat(ByteBinding.entryToByte(entry)).isEqualTo((byte) 8);
        CharacterBinding.charToEntry('a', entry);
        assertThat(CharacterBinding.entryToChar(entry)).isEqualTo('a');
        DoubleBinding.doubleToEntry(8.5, entry);
        assertThat(DoubleBinding.entryToDouble(entry)).isEqualTo(8.5);
        FloatBinding.floatToEntry(8.5f, entry);
        assertThat(FloatBinding.entryToFloat(entry)).isEqualTo(8.5f);
        IntegerBinding.intToEntry(8, entry);
        assertThat(IntegerBinding.entryToInt(entry)).isEqualTo(8);
        LongBinding.longToEntry(8L, entry);
        assertThat(LongBinding.entryToLong(entry)).isEqualTo(8L);
        ShortBinding.shortToEntry((short) 8, entry);
        assertThat(ShortBinding.entryToShort(entry)).isEqualTo((short) 8);
        StringBinding.stringToEntry("eight", entry);
        assertThat(StringBinding.entryToString(entry)).isEqualTo("eight");
        BigIntegerBinding.bigIntegerToEntry(BigInteger.valueOf(8), entry);
        assertThat(BigIntegerBinding.entryToBigInteger(entry)).isEqualTo(BigInteger.valueOf(8));
        SortedDoubleBinding.doubleToEntry(8.5, entry);
        assertThat(SortedDoubleBinding.entryToDouble(entry)).isEqualTo(8.5);
        SortedFloatBinding.floatToEntry(8.5f, entry);
        assertThat(SortedFloatBinding.entryToFloat(entry)).isEqualTo(8.5f);
        assertThat(Arrays.copyOf(entry.getData(), entry.getSize())).isNotEmpty();
    }

    private static Object roundTrip(TupleBinding binding, Object value) {
        TupleOutput output = new TupleOutput();
        binding.objectToEntry(value, output);
        return binding.entryToObject(new TupleInput(output));
    }
}
