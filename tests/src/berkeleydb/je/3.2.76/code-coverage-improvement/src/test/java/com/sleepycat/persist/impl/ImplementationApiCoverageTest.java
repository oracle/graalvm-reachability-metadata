/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.sleepycat.persist.impl;

import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.model.AnnotationModel;
import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.KeyField;
import com.sleepycat.persist.model.Persistent;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.raw.RawObject;
import com.sleepycat.persist.raw.RawType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ImplementationApiCoverageTest {

    @Test
    void primitiveFormatsDecodeTheirWireValues() {
        assertThat(new SimpleFormat.FBool(false).newInstance(input(bytesForBoolean(true)), false))
                .isEqualTo(true);
        assertThat(new SimpleFormat.FByte(false).newInstance(input(bytesForByte((byte) 7)), false))
                .isEqualTo((byte) 7);
        assertThat(new SimpleFormat.FShort(false).newInstance(input(bytesForShort((short) 19)), false))
                .isEqualTo((short) 19);
        assertThat(new SimpleFormat.FChar(false).newInstance(input(bytesForChar('q')), false))
                .isEqualTo('q');
        assertThat(new SimpleFormat.FFloat(false).newInstance(input(bytesForFloat(1.5f)), false))
                .isEqualTo(1.5f);
        assertThat(new SimpleFormat.FDouble(false).newInstance(input(bytesForDouble(2.5d)), false))
                .isEqualTo(2.5d);
    }

    @Test
    void persistentComparatorOrdersSerializedKeys() {
        PersistKeyBinding binding = new PersistKeyBinding(String.class, null);
        com.sleepycat.je.DatabaseEntry first = new com.sleepycat.je.DatabaseEntry();
        com.sleepycat.je.DatabaseEntry second = new com.sleepycat.je.DatabaseEntry();
        binding.objectToEntry("alpha", first);
        binding.objectToEntry("omega", second);

        PersistComparator comparator = new PersistComparator(String.class.getName(), null, binding);

        assertThat(comparator.compare(first.getData(), second.getData())).isNegative();
        assertThat(comparator.compare(second.getData(), first.getData())).isPositive();
    }

    @Test
    void catalogOpensExistingReferencesAndConvertsRawEntities(@TempDir Path home) throws Exception {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        PersistCatalog catalog = null;
        try {
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(true);
            AnnotationModel model = new AnnotationModel();
            model.registerClass(ConvertedRecord.class);
            catalog = new PersistCatalog(null, environment, "catalog", "catalog", databaseConfig,
                    model, null, false, null);
            Format format = catalog.getFormat(ConvertedRecord.class);
            assertThat(format.getSuperType()).isNull();
            assertThat(format.getComponentType()).isNull();
            assertThat(format.getDimensions()).isZero();
            assertThat(format.getEnumConstants()).isNull();
            assertThat(format.getFields()).containsKeys("id", "name");
            RawType rawFormat = format;
            assertThat(rawFormat.getSuperType()).isNull();
            assertThat(rawFormat.getComponentType()).isNull();
            assertThat(format.isEnum()).isFalse();
            assertThat(format.toString()).contains(ConvertedRecord.class.getName());
            ConvertedRecord keyRecord = new ConvertedRecord();
            format.readPriKey(keyRecord, input(new TupleOutput().writeInt(73).toByteArray()), false);
            assertThat(keyRecord.id).isEqualTo(73);

            Format primitiveArray = catalog.getFormat(int[].class);
            assertThat(primitiveArray.getDimensions()).isEqualTo(1);
            assertThat(primitiveArray.getComponentType()).isNotNull();
            RawType rawPrimitiveArray = primitiveArray;
            assertThat(rawPrimitiveArray.getComponentType()).isNotNull();
            assertThat(primitiveArray.isArray()).isTrue();
            Format objectArray = catalog.getFormat(String[][].class);
            assertThat(objectArray.getDimensions()).isEqualTo(2);
            assertThat(objectArray.getComponentType()).isNotNull();
            Format composite = catalog.getFormat(CompositeKey.class);
            assertThat(composite).isInstanceOf(CompositeKeyFormat.class);
            assertThat(((CompositeKeyFormat) composite).getFields())
                    .containsKeys("part", "sequence");
            Format enumFormat = catalog.getFormat(SampleEnum.class);
            assertThat(enumFormat.isEnum()).isTrue();
            assertThat(enumFormat.getEnumConstants()).containsExactly("FIRST", "SECOND");

            RawType type = format;
            Map<String, Object> values = new HashMap<>();
            values.put("id", 42);
            values.put("name", "converted");

            Object converted = catalog.convertRawObject(
                    new RawObject(type, values, null), new IdentityHashMap());

            assertThat(converted).isInstanceOf(ConvertedRecord.class);
            ConvertedRecord record = (ConvertedRecord) converted;
            assertThat(record.id).isEqualTo(42);
            assertThat(record.name).isEqualTo("converted");
            catalog.flush();
            catalog.openExisting();
            assertThat(catalog.close()).isFalse();
            assertThat(catalog.close()).isTrue();
            catalog = null;
        } finally {
            if (catalog != null) {
                catalog.close();
            }
            environment.close();
        }
    }

    private static RecordInput input(byte[] bytes) {
        return new RecordInput(null, false, null, 0, bytes, 0, bytes.length);
    }

    private static byte[] bytesForBoolean(boolean value) {
        return new TupleOutput().writeBoolean(value).toByteArray();
    }

    private static byte[] bytesForByte(byte value) {
        return new TupleOutput().writeByte(value).toByteArray();
    }

    private static byte[] bytesForShort(short value) {
        return new TupleOutput().writeShort(value).toByteArray();
    }

    private static byte[] bytesForChar(char value) {
        return new TupleOutput().writeChar(value).toByteArray();
    }

    private static byte[] bytesForFloat(float value) {
        return new TupleOutput().writeSortedFloat(value).toByteArray();
    }

    private static byte[] bytesForDouble(double value) {
        return new TupleOutput().writeSortedDouble(value).toByteArray();
    }

    @Persistent
    public static class CompositeKey {
        @KeyField(1)
        public String part;
        @KeyField(2)
        public int sequence;

        public CompositeKey() {
        }
    }

    @Entity
    public static class ConvertedRecord {
        @PrimaryKey
        public int id;
        public String name;

        public ConvertedRecord() {
        }
    }

    private enum SampleEnum {
        FIRST,
        SECOND
    }
}
