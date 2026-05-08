/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.ValueVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compare.Range;
import org.apache.arrow.vector.compare.RangeEqualsVisitor;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.StructVector;
import org.apache.arrow.vector.complex.impl.NullableStructWriter;
import org.apache.arrow.vector.complex.impl.UnionListWriter;
import org.apache.arrow.vector.complex.reader.FieldReader;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryEncoder;
import org.apache.arrow.vector.holders.NullableIntHolder;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.DictionaryEncoding;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;
import org.apache.arrow.vector.util.TransferPair;
import org.apache.arrow.vector.validate.ValidateVectorDataVisitor;
import org.apache.arrow.vector.validate.ValidateVectorVisitor;
import org.junit.jupiter.api.Test;

public class Arrow_vectorTest {
    @Test
    void fixedWidthVectorsSupportNullsReadersTransfersAndSchemaRootOperations() {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
            IntVector ids = new IntVector("id", allocator);
            Float8Vector scores = new Float8Vector("score", allocator);
            BitVector active = new BitVector("active", allocator);

            ids.allocateNew(5);
            scores.allocateNew(5);
            active.allocateNew(5);
            for (int index = 0; index < 5; index++) {
                ids.setSafe(index, index * 10);
                scores.setSafe(index, index + 0.25D);
                active.setSafe(index, index % 2);
            }
            ids.setNull(2);
            scores.setNull(4);
            ids.setValueCount(5);
            scores.setValueCount(5);
            active.setValueCount(5);

            assertThat(ids.getNullCount()).isEqualTo(1);
            assertThat(ids.getObject(2)).isNull();
            assertThat(scores.getObject(1)).isEqualTo(1.25D);
            assertThat(active.getObject(3)).isEqualTo(Boolean.TRUE);

            NullableIntHolder holder = new NullableIntHolder();
            ids.get(3, holder);
            assertThat(holder.isSet).isEqualTo(1);
            assertThat(holder.value).isEqualTo(30);

            FieldReader reader = ids.getReader();
            reader.setPosition(1);
            assertThat(reader.readInteger()).isEqualTo(10);
            reader.setPosition(2);
            assertThat(reader.isSet()).isFalse();

            TransferPair transferPair = ids.getTransferPair("idCopy", allocator);
            transferPair.splitAndTransfer(1, 3);
            try (ValueVector copied = transferPair.getTo()) {
                assertThat(copied.getValueCount()).isEqualTo(3);
                assertThat(copied.getObject(0)).isEqualTo(10);
                assertThat(copied.getObject(1)).isNull();
                assertThat(copied.getObject(2)).isEqualTo(30);
            }

            try (VectorSchemaRoot root = VectorSchemaRoot.of(ids, scores, active)) {
                root.setRowCount(5);
                assertThat(root.getSchema().getFields())
                        .extracting(Field::getName)
                        .containsExactly("id", "score", "active");
                assertThat(root.getVector("score")).isSameAs(scores);
                assertThat(root.contentToTSVString()).contains("id\tscore\tactive").contains("10\t1.25");

                try (VectorSchemaRoot slice = root.slice(1, 3)) {
                    assertThat(slice.getRowCount()).isEqualTo(3);
                    assertThat(slice.getVector("id").getObject(0)).isEqualTo(10);
                    assertThat(slice.getVector("id").getObject(1)).isNull();
                    assertThat(slice.getVector("active").getObject(2)).isEqualTo(Boolean.TRUE);
                }
            }
        }
    }

    @Test
    void variableWidthDecimalAndDictionaryVectorsRoundTripValues() {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
                VarCharVector words = new VarCharVector("words", allocator);
                VarBinaryVector payloads = new VarBinaryVector("payloads", allocator);
                DecimalVector prices = new DecimalVector("prices", allocator, 10, 2)) {
            words.allocateNew();
            words.setSafe(0, new Text("alpha"));
            words.setSafe(1, new Text("beta"));
            words.setSafe(2, new Text("alpha"));
            words.setSafe(3, new Text("gamma"));
            words.setValueCount(4);

            payloads.allocateNew();
            payloads.setSafe(0, "first".getBytes(StandardCharsets.UTF_8));
            payloads.setSafe(1, "second".getBytes(StandardCharsets.UTF_8));
            payloads.setNull(2);
            payloads.setValueCount(3);

            prices.allocateNew();
            prices.setSafe(0, new BigDecimal("12.34"));
            prices.setSafe(1, new BigDecimal("56.78"));
            prices.setNull(2);
            prices.setValueCount(3);

            assertThat(words.getObject(1).toString()).isEqualTo("beta");
            assertThat(payloads.get(0)).isEqualTo("first".getBytes(StandardCharsets.UTF_8));
            assertThat(payloads.getObject(2)).isNull();
            assertThat(prices.getObject(0)).isEqualByComparingTo("12.34");
            assertThat(prices.getPrecision()).isEqualTo(10);
            assertThat(prices.getScale()).isEqualTo(2);

            try (VarCharVector dictionaryValues = new VarCharVector("dictionary", allocator)) {
                dictionaryValues.allocateNew();
                dictionaryValues.setSafe(0, new Text("alpha"));
                dictionaryValues.setSafe(1, new Text("beta"));
                dictionaryValues.setSafe(2, new Text("gamma"));
                dictionaryValues.setValueCount(3);

                DictionaryEncoding encoding = new DictionaryEncoding(7L, false, new ArrowType.Int(8, true));
                Dictionary dictionary = new Dictionary(dictionaryValues, encoding);
                try (ValueVector encoded = DictionaryEncoder.encode(words, dictionary);
                        ValueVector decoded = DictionaryEncoder.decode(encoded, dictionary)) {
                    assertThat(encoded.getField().getDictionary()).isEqualTo(encoding);
                    assertThat(decoded.getValueCount()).isEqualTo(words.getValueCount());
                    assertThat(decoded.getObject(0).toString()).isEqualTo("alpha");
                    assertThat(decoded.getObject(1).toString()).isEqualTo("beta");
                    assertThat(decoded.getObject(2).toString()).isEqualTo("alpha");
                    assertThat(decoded.getObject(3).toString()).isEqualTo("gamma");
                    assertThat(DictionaryEncoder.getIndexType(dictionaryValues.getValueCount()).getBitWidth())
                            .isEqualTo(8);
                }
            }

            TransferPair copyPair = words.getTransferPair("wordsCopy", allocator);
            copyPair.copyValueSafe(3, 0);
            copyPair.copyValueSafe(0, 1);
            try (ValueVector copied = copyPair.getTo()) {
                copied.setValueCount(2);
                assertThat(copied.getObject(0).toString()).isEqualTo("gamma");
                assertThat(copied.getObject(1).toString()).isEqualTo("alpha");
                assertThat(new RangeEqualsVisitor(words, words).rangeEquals(new Range(0, 2, 1))).isTrue();
            }
        }
    }

    @Test
    void schemaJsonFlatbufferAndVectorCreationPreserveNestedFieldMetadata() throws IOException {
        Map<String, String> fieldMetadata = Map.of("unit", "test");
        Field id = new Field(
                "id",
                new FieldType(false, new ArrowType.Int(32, true), null, fieldMetadata),
                List.of());
        Field name = Field.nullable("name", ArrowType.Utf8.INSTANCE);
        Field score = Field.nullable("score", new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE));
        Field tags = new Field(
                "tags",
                FieldType.nullable(ArrowType.List.INSTANCE),
                List.of(Field.nullable("element", ArrowType.Utf8.INSTANCE)));
        Field person = new Field(
                "person",
                FieldType.nullable(ArrowType.Struct.INSTANCE),
                List.of(name, Field.nullable("age", new ArrowType.Int(32, true))));
        Schema schema = new Schema(List.of(id, score, tags, person), Map.of("owner", "arrow-vector-test"));

        String json = schema.toJson();
        Schema fromJson = Schema.fromJSON(json);
        Schema fromFlatBuffer = Schema.deserialize(ByteBuffer.wrap(schema.toByteArray()));

        assertThat(fromJson).isEqualTo(schema);
        assertThat(fromFlatBuffer).isEqualTo(schema);
        assertThat(fromJson.findField("person").getChildren())
                .extracting(Field::getName)
                .containsExactly("name", "age");
        assertThat(fromJson.findField("id").getMetadata()).containsEntry("unit", "test");
        assertThat(fromJson.getCustomMetadata()).containsEntry("owner", "arrow-vector-test");

        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
                VectorSchemaRoot root = VectorSchemaRoot.create(fromJson, allocator)) {
            root.allocateNew();
            root.setRowCount(2);

            assertThat(root.getVector("id")).isInstanceOf(IntVector.class);
            assertThat(root.getVector("score")).isInstanceOf(Float8Vector.class);
            assertThat(root.getVector("tags")).isInstanceOf(ListVector.class);
            assertThat(root.getVector("person")).isInstanceOf(StructVector.class);
            root.syncSchema();
            assertThat(root.getSchema().getFields())
                    .extracting(Field::getName)
                    .containsExactly("id", "score", "tags", "person");
        }
    }

    @Test
    void complexListStructAndFixedSizeListVectorsExposeNestedObjectsAndValidate() {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE);
                ListVector numbers = ListVector.empty("numbers", allocator);
                StructVector person = StructVector.empty("person", allocator);
                FixedSizeListVector coordinates = FixedSizeListVector.empty("coordinates", 2, allocator)) {
            writeIntegerLists(numbers);
            writeStructRows(person);
            writeFixedSizeLists(coordinates);

            assertThat(numbers.getObject(0)).isEqualTo(List.of(1, 2, 3));
            assertThat(numbers.getObject(1)).isNull();
            assertThat(numbers.getObject(2)).isEqualTo(List.of(5));

            assertThat(person.getObject(0)).isEqualTo(Map.of("age", 41));
            assertThat(person.getObject(1)).isNull();
            assertThat(person.getObject(2)).isEqualTo(Map.of("age", 7));

            assertThat(coordinates.getObject(0)).isEqualTo(List.of(1.5D, 2.5D));
            assertThat(coordinates.getObject(1)).isNull();
            assertThat(coordinates.getObject(2)).isEqualTo(List.of(-1.0D, 4.0D));

            numbers.accept(new ValidateVectorVisitor(), null);
            numbers.accept(new ValidateVectorDataVisitor(), null);
            person.accept(new ValidateVectorVisitor(), null);
            coordinates.accept(new ValidateVectorVisitor(), null);
        }
    }

    private static void writeIntegerLists(ListVector numbers) {
        UnionListWriter writer = numbers.getWriter();
        writer.allocate();

        writer.setPosition(0);
        writer.startList();
        writer.writeInt(1);
        writer.writeInt(2);
        writer.writeInt(3);
        writer.endList();

        writer.setPosition(1);
        writer.writeNull();

        writer.setPosition(2);
        writer.startList();
        writer.writeInt(5);
        writer.endList();

        writer.setValueCount(3);
    }

    private static void writeStructRows(StructVector person) {
        NullableStructWriter writer = person.getWriter();
        writer.allocate();

        writer.setPosition(0);
        writer.start();
        writer.integer("age").writeInt(41);
        writer.end();

        writer.setPosition(1);
        writer.writeNull();

        writer.setPosition(2);
        writer.start();
        writer.integer("age").writeInt(7);
        writer.end();

        writer.setValueCount(3);
    }

    private static void writeFixedSizeLists(FixedSizeListVector coordinates) {
        FieldType valueType = FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE));
        coordinates.addOrGetVector(valueType);
        coordinates.allocateNew();

        Float8Vector values = (Float8Vector) coordinates.getDataVector();
        coordinates.setNotNull(0);
        values.setSafe(0, 1.5D);
        values.setSafe(1, 2.5D);
        coordinates.setNull(1);
        coordinates.setNotNull(2);
        values.setSafe(4, -1.0D);
        values.setSafe(5, 4.0D);
        values.setValueCount(6);
        coordinates.setValueCount(3);
    }
}
