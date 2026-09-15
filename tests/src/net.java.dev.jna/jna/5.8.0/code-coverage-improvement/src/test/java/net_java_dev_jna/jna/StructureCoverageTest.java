/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureCoverageTest {
    @Structure.FieldOrder({"number", "text"})
    public static class Record extends Structure {
        public int number;
        public String text;

        public Record() {
        }

        public Record(int number, String text) {
            this.number = number;
            this.text = text;
        }

        public Record(Pointer pointer) {
            super(pointer);
        }
    }

    public static class RecordByValue extends Record implements Structure.ByValue {
        public RecordByValue() {
            super();
        }
    }

    public static class RecordReference extends Record implements Structure.ByReference {
        public RecordReference() {
            super();
        }
    }

    @Structure.FieldOrder({"chars", "numbers", "child"})
    public static class ArrayRecord extends Structure {
        public char[] chars = new char[2];
        public int[] numbers = new int[2];
        public RecordReference child = new RecordReference();
    }

    public static class Numbers extends Union {
        public int integer;
        public short small;
        public String string;
    }

    @Structure.FieldOrder({"child"})
    public static class InlineRecord extends Structure {
        public RecordByValue child = new RecordByValue();
    }

    public static class Unordered extends Structure {
        public int first;
        public int second;
    }

    @Structure.FieldOrder({"character"})
    public static class CharRecord extends Structure {
        public char character;
    }

    @Structure.FieldOrder({"value"})
    public static class Numeric extends Structure {
        public int value;
    }

    @Structure.FieldOrder({"value"})
    public static class UncachedRecord extends Structure {
        public int value;

        public UncachedRecord() {
        }

        public UncachedRecord(Pointer pointer) {
            super(pointer);
        }
    }

    @Test
    void structureReadWriteAndComparison() {
        Record first = new Record(7, "seven");
        Record second = new Record(7, "seven");
        Numeric firstNumeric = new Numeric();
        Numeric secondNumeric = new Numeric();
        firstNumeric.value = 7;
        secondNumeric.value = 7;
        assertThat(first.size()).isGreaterThan(0);
        first.write();
        second.write();
        firstNumeric.write();
        secondNumeric.write();
        assertThat(firstNumeric.dataEquals(secondNumeric)).isTrue();
        assertThat(firstNumeric.dataEquals(secondNumeric, true)).isTrue();
        assertThat(firstNumeric).isEqualTo(firstNumeric);
        assertThat(firstNumeric).isNotEqualTo(secondNumeric);
        assertThat(firstNumeric.hashCode()).isEqualTo(firstNumeric.hashCode());
        assertThat(first.toString()).contains("number").contains("seven");
        assertThat(first.toString(true)).contains("number");

        first.writeField("number", 12);
        assertThat(first.readField("number")).isEqualTo(12);
        first.text = "changed";
        first.writeField("text");
        assertThat(first.readField("text")).isEqualTo("changed");
        first.clear();
        first.read();
        assertThat(first.number).isEqualTo(0);
        assertThat(first.text).isNull();

        first.setAutoRead(false);
        first.setAutoWrite(false);
        assertThat(first.getAutoRead()).isFalse();
        assertThat(first.getAutoWrite()).isFalse();
        first.setAutoSynch(true);
        assertThat(first.getAutoRead()).isTrue();
        assertThat(first.getAutoWrite()).isTrue();
        first.autoWrite();
        first.autoRead();
        Structure[] contiguous = first.toArray(2);
        Structure.autoWrite(contiguous);
        Structure.autoRead(contiguous);
    }

    @Test
    void structureArraysAndFieldOrderHelpers() {
        Record record = new Record(1, "one");
        Structure[] supplied = new Record[2];
        Structure[] result = record.toArray(supplied);
        assertThat(result).hasSize(2);
        assertThat(result[0]).isSameAs(record);
        assertThat(new Record(1, "one").toArray(3)).hasSize(3);
        List<String> base = Arrays.asList("base");
        assertThat(Structure.createFieldsOrder("first")).containsExactly("first");
        assertThat(Structure.createFieldsOrder("first", "second")).containsExactly("first", "second");
        assertThat(Structure.createFieldsOrder(base, "extra", "last")).containsExactly("base", "extra", "last");
        assertThat(Structure.createFieldsOrder(base, Arrays.asList("extra"))).containsExactly("base", "extra");
    }

    @Test
    void unionUsesThePublicStructureContract() {
        Numbers numbers = new Numbers();
        Structure asStructure = numbers;
        asStructure.writeField("integer", 0x12345678);
        assertThat(asStructure.readField("integer")).isEqualTo(0x12345678);
        assertThat(numbers.integer).isEqualTo(0x12345678);
        numbers.setType(Short.TYPE);
        numbers.small = 123;
        asStructure.writeField("small");
        assertThat(asStructure.readField("small")).isEqualTo((short) 123);
        assertThat(numbers.getTypedValue(Short.TYPE)).isEqualTo((short) 123);
        assertThat(numbers.setTypedValue("ten")).isSameAs(numbers);
        assertThat(numbers.string).isEqualTo("ten");
        numbers.write();
        asStructure.autoRead();
        asStructure.autoWrite();
    }

    @Test
    void nativeSizeAndPlaceholderMemoryUsePublicEntries() {
        assertThat(Native.getNativeSize(UncachedRecord.class)).isGreaterThan(0);
        assertThat(Native.getNativeSize(UncachedRecord.class, new UncachedRecord())).isGreaterThan(0);
    }

    @Test
    void nestedValuesAndLayoutErrorsUsePublicStructureEntries() {
        InlineRecord inline = new InlineRecord();
        inline.child.number = 21;
        inline.clear();
        assertThat(inline.size()).isGreaterThan(0);
        assertThatThrownBy(() -> new Unordered().clear()).isInstanceOf(Error.class);

        CharRecord character = new CharRecord();
        character.writeField("character", 'z');
        assertThat(character.readField("character")).isEqualTo('z');
    }

    @Test
    void arrayAndByReferenceFieldsUsePublicReadWriteEntries() {
        ArrayRecord record = new ArrayRecord();
        record.chars = new char[]{'a', 'b'};
        record.numbers = new int[]{11, 12};
        record.child.number = 13;
        record.child.text = "child";
        record.write();
        record.chars = new char[2];
        record.numbers = new int[2];
        record.read();
        assertThat(record.chars).containsExactly('a', 'b');
        assertThat(record.numbers).containsExactly(11, 12);
        assertThat(record.child.number).isEqualTo(13);
        assertThat(record.child.text).isEqualTo("child");
        record.writeField("chars", new char[]{'x', 'y'});
        assertThat((char[]) record.readField("chars")).containsExactly('x', 'y');
    }

    @Test
    void structureRejectsAnOpaqueBackingPointer() {
        assertThatThrownBy(() -> new Record(Pointer.createConstant(0x1234L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
