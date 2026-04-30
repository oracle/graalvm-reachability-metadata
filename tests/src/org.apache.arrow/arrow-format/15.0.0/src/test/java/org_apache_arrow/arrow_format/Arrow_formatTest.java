/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_format;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.arrow.flatbuf.Binary;
import org.apache.arrow.flatbuf.Block;
import org.apache.arrow.flatbuf.BodyCompression;
import org.apache.arrow.flatbuf.BodyCompressionMethod;
import org.apache.arrow.flatbuf.Bool;
import org.apache.arrow.flatbuf.Buffer;
import org.apache.arrow.flatbuf.CompressionType;
import org.apache.arrow.flatbuf.Date;
import org.apache.arrow.flatbuf.DateUnit;
import org.apache.arrow.flatbuf.Decimal;
import org.apache.arrow.flatbuf.DictionaryBatch;
import org.apache.arrow.flatbuf.DictionaryEncoding;
import org.apache.arrow.flatbuf.DictionaryKind;
import org.apache.arrow.flatbuf.Duration;
import org.apache.arrow.flatbuf.Endianness;
import org.apache.arrow.flatbuf.Feature;
import org.apache.arrow.flatbuf.Field;
import org.apache.arrow.flatbuf.FieldNode;
import org.apache.arrow.flatbuf.FixedSizeBinary;
import org.apache.arrow.flatbuf.FixedSizeList;
import org.apache.arrow.flatbuf.FloatingPoint;
import org.apache.arrow.flatbuf.Footer;
import org.apache.arrow.flatbuf.Int;
import org.apache.arrow.flatbuf.Interval;
import org.apache.arrow.flatbuf.IntervalUnit;
import org.apache.arrow.flatbuf.KeyValue;
import org.apache.arrow.flatbuf.LargeBinary;
import org.apache.arrow.flatbuf.LargeList;
import org.apache.arrow.flatbuf.LargeUtf8;
import org.apache.arrow.flatbuf.List;
import org.apache.arrow.flatbuf.Map;
import org.apache.arrow.flatbuf.Message;
import org.apache.arrow.flatbuf.MessageHeader;
import org.apache.arrow.flatbuf.MetadataVersion;
import org.apache.arrow.flatbuf.Null;
import org.apache.arrow.flatbuf.Precision;
import org.apache.arrow.flatbuf.RecordBatch;
import org.apache.arrow.flatbuf.Schema;
import org.apache.arrow.flatbuf.SparseTensor;
import org.apache.arrow.flatbuf.SparseTensorIndex;
import org.apache.arrow.flatbuf.SparseTensorIndexCSF;
import org.apache.arrow.flatbuf.SparseTensorIndexCOO;
import org.apache.arrow.flatbuf.Struct_;
import org.apache.arrow.flatbuf.Tensor;
import org.apache.arrow.flatbuf.TensorDim;
import org.apache.arrow.flatbuf.Time;
import org.apache.arrow.flatbuf.TimeUnit;
import org.apache.arrow.flatbuf.Timestamp;
import org.apache.arrow.flatbuf.Type;
import org.apache.arrow.flatbuf.Union;
import org.apache.arrow.flatbuf.UnionMode;
import org.apache.arrow.flatbuf.Utf8;
import org.junit.jupiter.api.Test;

public class Arrow_formatTest {
    @Test
    void schemaPreservesNestedFieldsDictionariesMetadataAndFeatures() {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        int fieldMetadata = KeyValue.createKeyValue(
                builder, builder.createString("logicalName"), builder.createString("primary-key"));
        int fieldMetadataVector = Field.createCustomMetadataVector(builder, new int[] {fieldMetadata });
        int intType = Int.createInt(builder, 64, true);
        int dictionaryIndexType = Int.createInt(builder, 32, false);
        int dictionary = DictionaryEncoding.createDictionaryEncoding(
                builder, 42L, dictionaryIndexType, true, DictionaryKind.DenseArray);
        int idField = Field.createField(
                builder,
                builder.createString("id"),
                false,
                Type.Int,
                intType,
                dictionary,
                0,
                fieldMetadataVector);

        int tagChild = Field.createField(
                builder,
                builder.createString("item"),
                true,
                Type.Utf8,
                createUtf8(builder),
                0,
                0,
                0);
        int tagChildren = Field.createChildrenVector(builder, new int[] {tagChild });
        int tagsField = Field.createField(
                builder,
                builder.createString("tags"),
                true,
                Type.List,
                createList(builder),
                0,
                tagChildren,
                0);

        int schemaMetadata = KeyValue.createKeyValue(
                builder, builder.createString("producer"), builder.createString("integration-test"));
        int schemaMetadataVector = Schema.createCustomMetadataVector(builder, new int[] {schemaMetadata });
        int featuresVector = Schema.createFeaturesVector(
                builder, new long[] {Feature.DICTIONARY_REPLACEMENT, Feature.COMPRESSED_BODY });
        int fieldsVector = Schema.createFieldsVector(builder, new int[] {idField, tagsField });
        int schemaOffset = Schema.createSchema(
                builder, Endianness.Little, fieldsVector, schemaMetadataVector, featuresVector);
        Schema.finishSchemaBuffer(builder, schemaOffset);

        Schema schema = Schema.getRootAsSchema(builder.dataBuffer());

        assertThat(schema.endianness()).isEqualTo(Endianness.Little);
        assertThat(schema.fieldsLength()).isEqualTo(2);
        assertThat(schema.fieldsVector()).isNotNull();
        assertThat(schema.customMetadataLength()).isEqualTo(1);
        assertThat(schema.customMetadata(0).key()).isEqualTo("producer");
        assertThat(readUtf8(schema.customMetadata(0).valueAsByteBuffer())).isEqualTo("integration-test");
        assertThat(schema.featuresLength()).isEqualTo(2);
        assertThat(schema.features(0)).isEqualTo(Feature.DICTIONARY_REPLACEMENT);
        assertThat(schema.features(1)).isEqualTo(Feature.COMPRESSED_BODY);
        assertThat(schema.featuresVector()).isNotNull();

        Field id = schema.fields(0);
        assertThat(id.name()).isEqualTo("id");
        assertThat(id.nullable()).isFalse();
        assertThat(id.typeType()).isEqualTo(Type.Int);
        Int readInt = (Int) id.type(new Int());
        assertThat(readInt.bitWidth()).isEqualTo(64);
        assertThat(readInt.isSigned()).isTrue();
        assertThat(id.dictionary().id()).isEqualTo(42L);
        assertThat(id.dictionary().isOrdered()).isTrue();
        assertThat(id.dictionary().dictionaryKind()).isEqualTo(DictionaryKind.DenseArray);
        assertThat(id.dictionary().indexType().bitWidth()).isEqualTo(32);
        assertThat(id.dictionary().indexType().isSigned()).isFalse();
        assertThat(id.customMetadata(0).key()).isEqualTo("logicalName");
        assertThat(id.customMetadata(0).value()).isEqualTo("primary-key");

        Field tags = schema.fields(1);
        assertThat(tags.name()).isEqualTo("tags");
        assertThat(tags.nullable()).isTrue();
        assertThat(tags.typeType()).isEqualTo(Type.List);
        assertThat(tags.type(new List())).isInstanceOf(List.class);
        assertThat(tags.childrenLength()).isEqualTo(1);
        assertThat(tags.childrenVector()).isNotNull();
        assertThat(tags.children(0).name()).isEqualTo("item");
        assertThat(tags.children(0).typeType()).isEqualTo(Type.Utf8);
        assertThat(tags.children(0).type(new Utf8())).isInstanceOf(Utf8.class);
    }

    @Test
    void fieldTypeVariantsExposeTheirSpecificAttributes() {
        FlatBufferBuilder builder = new FlatBufferBuilder(2048);

        int fixedSizeListChild = Field.createField(
                builder,
                builder.createString("value"),
                false,
                Type.FloatingPoint,
                FloatingPoint.createFloatingPoint(builder, Precision.SINGLE),
                0,
                0,
                0);
        int fixedSizeListChildren = Field.createChildrenVector(builder, new int[] {fixedSizeListChild });
        int mapKey = Field.createField(
                builder, builder.createString("key"), false, Type.Utf8, createUtf8(builder), 0, 0, 0);
        int mapValue = Field.createField(
                builder, builder.createString("value"), true, Type.Binary, createBinary(builder), 0, 0, 0);
        int mapChildren = Field.createChildrenVector(builder, new int[] {mapKey, mapValue });
        int unionTypeIds = Union.createTypeIdsVector(builder, new int[] {7, 8 });

        int[] fields = new int[] {
            createField(
                    builder,
                    "doubleValue",
                    Type.FloatingPoint,
                    FloatingPoint.createFloatingPoint(builder, Precision.DOUBLE)),
            createField(builder, "money", Type.Decimal, Decimal.createDecimal(builder, 38, 4, 128)),
            createField(builder, "eventDate", Type.Date, Date.createDate(builder, DateUnit.MILLISECOND)),
            createField(builder, "eventTime", Type.Time, Time.createTime(builder, TimeUnit.MICROSECOND, 64)),
            createField(
                    builder,
                    "createdAt",
                    Type.Timestamp,
                    Timestamp.createTimestamp(builder, TimeUnit.NANOSECOND, builder.createString("UTC"))),
            createField(builder, "elapsed", Type.Duration, Duration.createDuration(builder, TimeUnit.MILLISECOND)),
            createField(
                    builder,
                    "window",
                    Type.Interval,
                    Interval.createInterval(builder, IntervalUnit.MONTH_DAY_NANO)),
            createField(builder, "uuid", Type.FixedSizeBinary, FixedSizeBinary.createFixedSizeBinary(builder, 16)),
            Field.createField(
                    builder,
                    builder.createString("triple"),
                    false,
                    Type.FixedSizeList,
                    FixedSizeList.createFixedSizeList(builder, 3),
                    0,
                    fixedSizeListChildren,
                    0),
            Field.createField(
                    builder,
                    builder.createString("attributes"),
                    false,
                    Type.Map,
                    Map.createMap(builder, true),
                    0,
                    mapChildren,
                    0),
            createField(builder, "choice", Type.Union, Union.createUnion(builder, UnionMode.Dense, unionTypeIds)),
            createField(builder, "largeText", Type.LargeUtf8, createLargeUtf8(builder)),
            createField(builder, "present", Type.Bool, createBool(builder)),
            createField(builder, "nothing", Type.Null, createNull(builder)),
            createField(builder, "record", Type.Struct_, createStruct(builder)),
            createField(builder, "largeBytes", Type.LargeBinary, createLargeBinary(builder)),
            createField(builder, "largeList", Type.LargeList, createLargeList(builder))
        };
        int schemaOffset = Schema.createSchema(
                builder, Endianness.Big, Schema.createFieldsVector(builder, fields), 0, 0);
        Schema.finishSchemaBuffer(builder, schemaOffset);

        Schema schema = Schema.getRootAsSchema(builder.dataBuffer());

        assertThat(schema.endianness()).isEqualTo(Endianness.Big);
        assertThat(schema.fieldsLength()).isEqualTo(fields.length);
        FloatingPoint doubleValue = (FloatingPoint) schema.fields(0).type(new FloatingPoint());
        assertThat(doubleValue.precision()).isEqualTo(Precision.DOUBLE);
        Decimal decimal = (Decimal) schema.fields(1).type(new Decimal());
        assertThat(decimal.precision()).isEqualTo(38);
        assertThat(decimal.scale()).isEqualTo(4);
        assertThat(decimal.bitWidth()).isEqualTo(128);
        assertThat(((Date) schema.fields(2).type(new Date())).unit()).isEqualTo(DateUnit.MILLISECOND);
        Time time = (Time) schema.fields(3).type(new Time());
        assertThat(time.unit()).isEqualTo(TimeUnit.MICROSECOND);
        assertThat(time.bitWidth()).isEqualTo(64);
        Timestamp timestamp = (Timestamp) schema.fields(4).type(new Timestamp());
        assertThat(timestamp.unit()).isEqualTo(TimeUnit.NANOSECOND);
        assertThat(timestamp.timezone()).isEqualTo("UTC");
        assertThat(readUtf8(timestamp.timezoneAsByteBuffer())).isEqualTo("UTC");
        assertThat(((Duration) schema.fields(5).type(new Duration())).unit()).isEqualTo(TimeUnit.MILLISECOND);
        assertThat(((Interval) schema.fields(6).type(new Interval())).unit()).isEqualTo(IntervalUnit.MONTH_DAY_NANO);
        assertThat(((FixedSizeBinary) schema.fields(7).type(new FixedSizeBinary())).byteWidth()).isEqualTo(16);
        assertThat(((FixedSizeList) schema.fields(8).type(new FixedSizeList())).listSize()).isEqualTo(3);
        assertThat(schema.fields(8).childrenLength()).isEqualTo(1);
        assertThat(((Map) schema.fields(9).type(new Map())).keysSorted()).isTrue();
        assertThat(schema.fields(9).childrenLength()).isEqualTo(2);
        Union union = (Union) schema.fields(10).type(new Union());
        assertThat(union.mode()).isEqualTo(UnionMode.Dense);
        assertThat(union.typeIdsLength()).isEqualTo(2);
        assertThat(union.typeIds(0)).isEqualTo(7);
        assertThat(union.typeIds(1)).isEqualTo(8);
        assertThat(union.typeIdsVector()).isNotNull();
        assertThat(schema.fields(11).type(new LargeUtf8())).isInstanceOf(LargeUtf8.class);
        assertThat(schema.fields(12).type(new Bool())).isInstanceOf(Bool.class);
        assertThat(schema.fields(13).type(new Null())).isInstanceOf(Null.class);
        assertThat(schema.fields(14).type(new Struct_())).isInstanceOf(Struct_.class);
        assertThat(schema.fields(15).type(new LargeBinary())).isInstanceOf(LargeBinary.class);
        assertThat(schema.fields(16).type(new LargeList())).isInstanceOf(LargeList.class);
    }

    @Test
    void messageCarriesRecordBatchHeaderWithNodesBuffersCompressionAndMetadata() {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        int nodesVector = createFieldNodesVector(builder, new long[][] {{5L, 0L }, {5L, 1L } });
        int buffersVector = createBuffersVector(builder, new long[][] {{0L, 1L }, {8L, 40L }, {48L, 8L } });
        int compression = BodyCompression.createBodyCompression(
                builder, CompressionType.ZSTD, BodyCompressionMethod.BUFFER);
        int variadicBufferCounts = RecordBatch.createVariadicBufferCountsVector(builder, new long[] {2L });
        int recordBatchOffset = RecordBatch.createRecordBatch(
                builder, 5L, nodesVector, buffersVector, compression, variadicBufferCounts);
        int metadata = KeyValue.createKeyValue(
                builder, builder.createString("batch"), builder.createString("compressed"));
        int metadataVector = Message.createCustomMetadataVector(builder, new int[] {metadata });
        int messageOffset = Message.createMessage(
                builder, MetadataVersion.V5, MessageHeader.RecordBatch, recordBatchOffset, 56L, metadataVector);
        Message.finishMessageBuffer(builder, messageOffset);

        Message message = Message.getRootAsMessage(builder.dataBuffer());

        assertThat(message.version()).isEqualTo(MetadataVersion.V5);
        assertThat(message.headerType()).isEqualTo(MessageHeader.RecordBatch);
        assertThat(message.bodyLength()).isEqualTo(56L);
        assertThat(message.customMetadataLength()).isEqualTo(1);
        assertThat(message.customMetadata(0).key()).isEqualTo("batch");
        assertThat(message.customMetadata(0).value()).isEqualTo("compressed");

        RecordBatch recordBatch = (RecordBatch) message.header(new RecordBatch());
        assertThat(recordBatch.length()).isEqualTo(5L);
        assertThat(recordBatch.nodesLength()).isEqualTo(2);
        assertThat(recordBatch.nodesVector()).isNotNull();
        assertThat(recordBatch.nodes(0).length()).isEqualTo(5L);
        assertThat(recordBatch.nodes(0).nullCount()).isZero();
        assertThat(recordBatch.nodes(1).nullCount()).isEqualTo(1L);
        assertThat(recordBatch.buffersLength()).isEqualTo(3);
        assertThat(recordBatch.buffersVector()).isNotNull();
        assertThat(recordBatch.buffers(1).offset()).isEqualTo(8L);
        assertThat(recordBatch.buffers(1).length()).isEqualTo(40L);
        assertThat(recordBatch.compression().codec()).isEqualTo(CompressionType.ZSTD);
        assertThat(recordBatch.compression().method()).isEqualTo(BodyCompressionMethod.BUFFER);
        assertThat(recordBatch.variadicBufferCountsLength()).isEqualTo(1);
        assertThat(recordBatch.variadicBufferCounts(0)).isEqualTo(2L);
        assertThat(recordBatch.variadicBufferCountsVector()).isNotNull();
    }

    @Test
    void dictionaryBatchMessageCarriesDictionaryIdDeltaFlagAndDataBatch() {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        int nodesVector = createFieldNodesVector(builder, new long[][] {{3L, 0L } });
        int buffersVector = createBuffersVector(builder, new long[][] {{0L, 1L }, {8L, 12L } });
        int recordBatchOffset = RecordBatch.createRecordBatch(builder, 3L, nodesVector, buffersVector, 0, 0);
        int dictionaryBatchOffset = DictionaryBatch.createDictionaryBatch(builder, 7L, recordBatchOffset, true);
        int messageOffset = Message.createMessage(
                builder, MetadataVersion.V5, MessageHeader.DictionaryBatch, dictionaryBatchOffset, 20L, 0);
        Message.finishMessageBuffer(builder, messageOffset);

        Message message = Message.getRootAsMessage(builder.dataBuffer());

        assertThat(message.version()).isEqualTo(MetadataVersion.V5);
        assertThat(message.headerType()).isEqualTo(MessageHeader.DictionaryBatch);
        assertThat(message.bodyLength()).isEqualTo(20L);
        DictionaryBatch dictionaryBatch = (DictionaryBatch) message.header(new DictionaryBatch());
        assertThat(dictionaryBatch.id()).isEqualTo(7L);
        assertThat(dictionaryBatch.isDelta()).isTrue();
        assertThat(dictionaryBatch.data().length()).isEqualTo(3L);
        assertThat(dictionaryBatch.data().nodesLength()).isEqualTo(1);
        assertThat(dictionaryBatch.data().nodes(0).nullCount()).isZero();
        assertThat(dictionaryBatch.data().buffersLength()).isEqualTo(2);
        assertThat(dictionaryBatch.data().buffers(1).offset()).isEqualTo(8L);
        assertThat(dictionaryBatch.data().buffers(1).length()).isEqualTo(12L);
    }

    @Test
    void footerReferencesSchemaAndDictionaryAndRecordBatchBlocks() {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);

        int valueField = createField(builder, "value", Type.Int, Int.createInt(builder, 32, true));
        int schema = Schema.createSchema(
                builder,
                Endianness.Little,
                Schema.createFieldsVector(builder, new int[] {valueField }),
                0,
                0);
        int dictionaryBlocks = createBlocksVector(builder, new long[][] {{128L, 24L, 64L } }, true);
        int recordBatchBlocks = createBlocksVector(
                builder, new long[][] {{256L, 32L, 96L }, {384L, 40L, 128L } }, false);
        int metadata = KeyValue.createKeyValue(
                builder, builder.createString("footer"), builder.createString("complete"));
        int footerOffset = Footer.createFooter(
                builder,
                MetadataVersion.V4,
                schema,
                dictionaryBlocks,
                recordBatchBlocks,
                Footer.createCustomMetadataVector(builder, new int[] {metadata }));
        Footer.finishFooterBuffer(builder, footerOffset);

        Footer footer = Footer.getRootAsFooter(builder.dataBuffer());

        assertThat(footer.version()).isEqualTo(MetadataVersion.V4);
        assertThat(footer.schema().fieldsLength()).isEqualTo(1);
        assertThat(footer.schema().fields(0).name()).isEqualTo("value");
        assertThat(footer.dictionariesLength()).isEqualTo(1);
        assertThat(footer.dictionariesVector()).isNotNull();
        assertThat(footer.dictionaries(0).offset()).isEqualTo(128L);
        assertThat(footer.dictionaries(0).metaDataLength()).isEqualTo(24);
        assertThat(footer.dictionaries(0).bodyLength()).isEqualTo(64L);
        assertThat(footer.recordBatchesLength()).isEqualTo(2);
        assertThat(footer.recordBatchesVector()).isNotNull();
        assertThat(footer.recordBatches(1).offset()).isEqualTo(384L);
        assertThat(footer.recordBatches(1).metaDataLength()).isEqualTo(40);
        assertThat(footer.recordBatches(1).bodyLength()).isEqualTo(128L);
        assertThat(footer.customMetadataLength()).isEqualTo(1);
        assertThat(footer.customMetadata(0).key()).isEqualTo("footer");
        assertThat(footer.customMetadata(0).value()).isEqualTo("complete");
    }

    @Test
    void denseAndSparseTensorMetadataRoundTripsShapeStridesAndDataBuffers() {
        FlatBufferBuilder denseBuilder = new FlatBufferBuilder(1024);
        int denseTensorOffset = createDenseTensor(denseBuilder);
        Tensor.finishTensorBuffer(denseBuilder, denseTensorOffset);

        Tensor tensor = Tensor.getRootAsTensor(denseBuilder.dataBuffer());

        assertThat(tensor.typeType()).isEqualTo(Type.FloatingPoint);
        assertThat(((FloatingPoint) tensor.type(new FloatingPoint())).precision()).isEqualTo(Precision.DOUBLE);
        assertThat(tensor.shapeLength()).isEqualTo(2);
        assertThat(tensor.shapeVector()).isNotNull();
        assertThat(tensor.shape(0).name()).isEqualTo("rows");
        assertThat(tensor.shape(0).size()).isEqualTo(2L);
        assertThat(tensor.shape(1).name()).isEqualTo("columns");
        assertThat(tensor.shape(1).size()).isEqualTo(3L);
        assertThat(tensor.stridesLength()).isEqualTo(2);
        assertThat(tensor.strides(0)).isEqualTo(24L);
        assertThat(tensor.strides(1)).isEqualTo(8L);
        assertThat(tensor.stridesVector()).isNotNull();
        assertThat(tensor.data().offset()).isEqualTo(64L);
        assertThat(tensor.data().length()).isEqualTo(48L);

        FlatBufferBuilder sparseBuilder = new FlatBufferBuilder(1024);
        int sparseTensorOffset = createSparseTensor(sparseBuilder);
        SparseTensor.finishSparseTensorBuffer(sparseBuilder, sparseTensorOffset);

        SparseTensor sparseTensor = SparseTensor.getRootAsSparseTensor(sparseBuilder.dataBuffer());

        assertThat(sparseTensor.typeType()).isEqualTo(Type.Int);
        assertThat(((Int) sparseTensor.type(new Int())).bitWidth()).isEqualTo(32);
        assertThat(sparseTensor.shapeLength()).isEqualTo(2);
        assertThat(sparseTensor.nonZeroLength()).isEqualTo(3L);
        assertThat(sparseTensor.sparseIndexType()).isEqualTo(SparseTensorIndex.SparseTensorIndexCOO);
        SparseTensorIndexCOO cooIndex = (SparseTensorIndexCOO) sparseTensor.sparseIndex(new SparseTensorIndexCOO());
        assertThat(cooIndex.indicesType().bitWidth()).isEqualTo(64);
        assertThat(cooIndex.indicesStridesLength()).isEqualTo(2);
        assertThat(cooIndex.indicesStrides(0)).isEqualTo(16L);
        assertThat(cooIndex.indicesStrides(1)).isEqualTo(8L);
        assertThat(cooIndex.indicesBuffer().offset()).isEqualTo(0L);
        assertThat(cooIndex.indicesBuffer().length()).isEqualTo(48L);
        assertThat(cooIndex.isCanonical()).isTrue();
        assertThat(sparseTensor.data().offset()).isEqualTo(48L);
        assertThat(sparseTensor.data().length()).isEqualTo(12L);
    }

    @Test
    void sparseTensorCsfIndexRoundTripsAxisOrderAndIndexBuffers() {
        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int sparseTensorOffset = createCsfSparseTensor(builder);
        SparseTensor.finishSparseTensorBuffer(builder, sparseTensorOffset);

        SparseTensor sparseTensor = SparseTensor.getRootAsSparseTensor(builder.dataBuffer());

        assertThat(sparseTensor.typeType()).isEqualTo(Type.Int);
        assertThat(((Int) sparseTensor.type(new Int())).bitWidth()).isEqualTo(64);
        assertThat(sparseTensor.shapeLength()).isEqualTo(3);
        assertThat(sparseTensor.shape(0).name()).isEqualTo("batch");
        assertThat(sparseTensor.shape(0).size()).isEqualTo(2L);
        assertThat(sparseTensor.shape(1).name()).isEqualTo("rows");
        assertThat(sparseTensor.shape(1).size()).isEqualTo(3L);
        assertThat(sparseTensor.shape(2).name()).isEqualTo("columns");
        assertThat(sparseTensor.shape(2).size()).isEqualTo(4L);
        assertThat(sparseTensor.nonZeroLength()).isEqualTo(4L);
        assertThat(sparseTensor.sparseIndexType()).isEqualTo(SparseTensorIndex.SparseTensorIndexCSF);

        SparseTensorIndexCSF csfIndex = (SparseTensorIndexCSF) sparseTensor.sparseIndex(new SparseTensorIndexCSF());
        assertThat(csfIndex.indptrType().bitWidth()).isEqualTo(32);
        assertThat(csfIndex.indptrType().isSigned()).isFalse();
        assertThat(csfIndex.indptrBuffersLength()).isEqualTo(2);
        assertThat(csfIndex.indptrBuffersVector()).isNotNull();
        assertThat(csfIndex.indptrBuffers(0).offset()).isEqualTo(0L);
        assertThat(csfIndex.indptrBuffers(0).length()).isEqualTo(12L);
        assertThat(csfIndex.indptrBuffers(1).offset()).isEqualTo(12L);
        assertThat(csfIndex.indptrBuffers(1).length()).isEqualTo(16L);
        assertThat(csfIndex.indicesType().bitWidth()).isEqualTo(64);
        assertThat(csfIndex.indicesType().isSigned()).isTrue();
        assertThat(csfIndex.indicesBuffersLength()).isEqualTo(3);
        assertThat(csfIndex.indicesBuffersVector()).isNotNull();
        assertThat(csfIndex.indicesBuffers(2).offset()).isEqualTo(64L);
        assertThat(csfIndex.indicesBuffers(2).length()).isEqualTo(32L);
        assertThat(csfIndex.axisOrderLength()).isEqualTo(3);
        assertThat(csfIndex.axisOrder(0)).isEqualTo(1);
        assertThat(csfIndex.axisOrder(1)).isEqualTo(2);
        assertThat(csfIndex.axisOrder(2)).isZero();
        assertThat(csfIndex.axisOrderVector()).isNotNull();
        assertThat(sparseTensor.data().offset()).isEqualTo(96L);
        assertThat(sparseTensor.data().length()).isEqualTo(32L);
    }

    @Test
    void enumNameLookupsExposeArrowFormatVocabulary() {
        assertThat(Type.name(Type.Int)).isEqualTo("Int");
        assertThat(Type.name(Type.Struct_)).isEqualTo("Struct_");
        assertThat(MetadataVersion.name(MetadataVersion.V5)).isEqualTo("V5");
        assertThat(Endianness.name(Endianness.Little)).isEqualTo("Little");
        assertThat(MessageHeader.name(MessageHeader.SparseTensor)).isEqualTo("SparseTensor");
        assertThat(DictionaryKind.name(DictionaryKind.DenseArray)).isEqualTo("DenseArray");
        assertThat(CompressionType.name(CompressionType.ZSTD)).isEqualTo("ZSTD");
        assertThat(BodyCompressionMethod.name(BodyCompressionMethod.BUFFER)).isEqualTo("BUFFER");
        assertThat(Feature.DICTIONARY_REPLACEMENT).isEqualTo(1L);
        assertThat(Feature.COMPRESSED_BODY).isEqualTo(2L);
        assertThat(DateUnit.name(DateUnit.DAY)).isEqualTo("DAY");
        assertThat(TimeUnit.name(TimeUnit.NANOSECOND)).isEqualTo("NANOSECOND");
        assertThat(Precision.name(Precision.HALF)).isEqualTo("HALF");
        assertThat(UnionMode.name(UnionMode.Sparse)).isEqualTo("Sparse");
        assertThat(SparseTensorIndex.name(SparseTensorIndex.SparseTensorIndexCOO)).isEqualTo("SparseTensorIndexCOO");
    }

    private static int createField(FlatBufferBuilder builder, String name, byte typeType, int type) {
        return Field.createField(builder, builder.createString(name), false, typeType, type, 0, 0, 0);
    }

    private static int createUtf8(FlatBufferBuilder builder) {
        Utf8.startUtf8(builder);
        return Utf8.endUtf8(builder);
    }

    private static int createLargeUtf8(FlatBufferBuilder builder) {
        LargeUtf8.startLargeUtf8(builder);
        return LargeUtf8.endLargeUtf8(builder);
    }

    private static int createBool(FlatBufferBuilder builder) {
        Bool.startBool(builder);
        return Bool.endBool(builder);
    }

    private static int createNull(FlatBufferBuilder builder) {
        Null.startNull(builder);
        return Null.endNull(builder);
    }

    private static int createStruct(FlatBufferBuilder builder) {
        Struct_.startStruct_(builder);
        return Struct_.endStruct_(builder);
    }

    private static int createLargeBinary(FlatBufferBuilder builder) {
        LargeBinary.startLargeBinary(builder);
        return LargeBinary.endLargeBinary(builder);
    }

    private static int createLargeList(FlatBufferBuilder builder) {
        LargeList.startLargeList(builder);
        return LargeList.endLargeList(builder);
    }

    private static int createBinary(FlatBufferBuilder builder) {
        Binary.startBinary(builder);
        return Binary.endBinary(builder);
    }

    private static int createList(FlatBufferBuilder builder) {
        List.startList(builder);
        return List.endList(builder);
    }

    private static int createFieldNodesVector(FlatBufferBuilder builder, long[][] nodes) {
        RecordBatch.startNodesVector(builder, nodes.length);
        for (int i = nodes.length - 1; i >= 0; i--) {
            FieldNode.createFieldNode(builder, nodes[i][0], nodes[i][1]);
        }
        return builder.endVector();
    }

    private static int createBuffersVector(FlatBufferBuilder builder, long[][] buffers) {
        RecordBatch.startBuffersVector(builder, buffers.length);
        for (int i = buffers.length - 1; i >= 0; i--) {
            Buffer.createBuffer(builder, buffers[i][0], buffers[i][1]);
        }
        return builder.endVector();
    }

    private static int createBlocksVector(FlatBufferBuilder builder, long[][] blocks, boolean dictionaries) {
        if (dictionaries) {
            Footer.startDictionariesVector(builder, blocks.length);
        } else {
            Footer.startRecordBatchesVector(builder, blocks.length);
        }
        for (int i = blocks.length - 1; i >= 0; i--) {
            Block.createBlock(builder, blocks[i][0], (int) blocks[i][1], blocks[i][2]);
        }
        return builder.endVector();
    }

    private static int createDenseTensor(FlatBufferBuilder builder) {
        int rows = TensorDim.createTensorDim(builder, 2L, builder.createString("rows"));
        int columns = TensorDim.createTensorDim(builder, 3L, builder.createString("columns"));
        int shape = Tensor.createShapeVector(builder, new int[] {rows, columns });
        int strides = Tensor.createStridesVector(builder, new long[] {24L, 8L });
        int type = FloatingPoint.createFloatingPoint(builder, Precision.DOUBLE);
        Tensor.startTensor(builder);
        Tensor.addTypeType(builder, Type.FloatingPoint);
        Tensor.addType(builder, type);
        Tensor.addShape(builder, shape);
        Tensor.addStrides(builder, strides);
        Tensor.addData(builder, Buffer.createBuffer(builder, 64L, 48L));
        return Tensor.endTensor(builder);
    }

    private static int createSparseTensor(FlatBufferBuilder builder) {
        int rows = TensorDim.createTensorDim(builder, 4L, builder.createString("rows"));
        int columns = TensorDim.createTensorDim(builder, 4L, builder.createString("columns"));
        int shape = SparseTensor.createShapeVector(builder, new int[] {rows, columns });
        int valueType = Int.createInt(builder, 32, true);
        int indicesType = Int.createInt(builder, 64, true);
        int indicesStrides = SparseTensorIndexCOO.createIndicesStridesVector(builder, new long[] {16L, 8L });
        SparseTensorIndexCOO.startSparseTensorIndexCOO(builder);
        SparseTensorIndexCOO.addIndicesType(builder, indicesType);
        SparseTensorIndexCOO.addIndicesStrides(builder, indicesStrides);
        SparseTensorIndexCOO.addIndicesBuffer(builder, Buffer.createBuffer(builder, 0L, 48L));
        SparseTensorIndexCOO.addIsCanonical(builder, true);
        int sparseIndex = SparseTensorIndexCOO.endSparseTensorIndexCOO(builder);
        SparseTensor.startSparseTensor(builder);
        SparseTensor.addTypeType(builder, Type.Int);
        SparseTensor.addType(builder, valueType);
        SparseTensor.addShape(builder, shape);
        SparseTensor.addNonZeroLength(builder, 3L);
        SparseTensor.addSparseIndexType(builder, SparseTensorIndex.SparseTensorIndexCOO);
        SparseTensor.addSparseIndex(builder, sparseIndex);
        SparseTensor.addData(builder, Buffer.createBuffer(builder, 48L, 12L));
        return SparseTensor.endSparseTensor(builder);
    }

    private static int createCsfSparseTensor(FlatBufferBuilder builder) {
        int batch = TensorDim.createTensorDim(builder, 2L, builder.createString("batch"));
        int rows = TensorDim.createTensorDim(builder, 3L, builder.createString("rows"));
        int columns = TensorDim.createTensorDim(builder, 4L, builder.createString("columns"));
        int shape = SparseTensor.createShapeVector(builder, new int[] {batch, rows, columns });
        int valueType = Int.createInt(builder, 64, true);
        int indptrType = Int.createInt(builder, 32, false);
        int indptrBuffers = createCsfIndptrBuffersVector(builder, new long[][] {{0L, 12L }, {12L, 16L } });
        int indicesType = Int.createInt(builder, 64, true);
        int indicesBuffers = createCsfIndicesBuffersVector(
                builder, new long[][] {{28L, 16L }, {44L, 20L }, {64L, 32L } });
        int axisOrder = SparseTensorIndexCSF.createAxisOrderVector(builder, new int[] {1, 2, 0 });
        int sparseIndex = SparseTensorIndexCSF.createSparseTensorIndexCSF(
                builder, indptrType, indptrBuffers, indicesType, indicesBuffers, axisOrder);
        SparseTensor.startSparseTensor(builder);
        SparseTensor.addTypeType(builder, Type.Int);
        SparseTensor.addType(builder, valueType);
        SparseTensor.addShape(builder, shape);
        SparseTensor.addNonZeroLength(builder, 4L);
        SparseTensor.addSparseIndexType(builder, SparseTensorIndex.SparseTensorIndexCSF);
        SparseTensor.addSparseIndex(builder, sparseIndex);
        SparseTensor.addData(builder, Buffer.createBuffer(builder, 96L, 32L));
        return SparseTensor.endSparseTensor(builder);
    }

    private static int createCsfIndptrBuffersVector(FlatBufferBuilder builder, long[][] buffers) {
        SparseTensorIndexCSF.startIndptrBuffersVector(builder, buffers.length);
        for (int i = buffers.length - 1; i >= 0; i--) {
            Buffer.createBuffer(builder, buffers[i][0], buffers[i][1]);
        }
        return builder.endVector();
    }

    private static int createCsfIndicesBuffersVector(FlatBufferBuilder builder, long[][] buffers) {
        SparseTensorIndexCSF.startIndicesBuffersVector(builder, buffers.length);
        for (int i = buffers.length - 1; i >= 0; i--) {
            Buffer.createBuffer(builder, buffers[i][0], buffers[i][1]);
        }
        return builder.endVector();
    }

    private static String readUtf8(ByteBuffer byteBuffer) {
        ByteBuffer copy = byteBuffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
