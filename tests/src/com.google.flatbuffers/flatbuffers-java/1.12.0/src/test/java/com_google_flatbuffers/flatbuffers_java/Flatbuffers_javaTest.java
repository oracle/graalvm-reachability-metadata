/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_flatbuffers.flatbuffers_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.flatbuffers.ArrayReadWriteBuf;
import com.google.flatbuffers.ByteBufferReadWriteBuf;
import com.google.flatbuffers.ByteBufferUtil;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class Flatbuffers_javaTest {
    private static final int FIELD_WIDTH = Short.BYTES;
    private static final int VTABLE_HEADER_SIZE = 2 * Short.BYTES;

    @Test
    void flatBufferBuilderCreatesReadableTablesStringsAndVectors() throws IOException {
        FlatBufferBuilder builder = new FlatBufferBuilder(1);
        int nameOffset = builder.createString("\u00c5da Lovelace");
        int payloadOffset = builder.createByteVector(new byte[] {10, 20, 30, 40});

        builder.startVector(Integer.BYTES, 3, Integer.BYTES);
        builder.addInt(300);
        builder.addInt(200);
        builder.addInt(100);
        int scoresOffset = builder.endVector();

        builder.startTable(5);
        builder.addInt(0, 42, 0);
        builder.addOffset(1, nameOffset, 0);
        builder.addOffset(2, scoresOffset, 0);
        builder.addBoolean(3, true, false);
        builder.addOffset(4, payloadOffset, 0);
        int tableOffset = builder.endTable();
        builder.finish(tableOffset, "PERS");

        ByteBuffer buffer = builder.dataBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int table = rootTable(buffer);

        assertThat(fileIdentifier(buffer)).isEqualTo("PERS");
        assertThat(readIntField(buffer, table, 0)).isEqualTo(42);
        assertThat(readStringField(buffer, table, 1)).isEqualTo("\u00c5da Lovelace");
        assertThat(readIntVectorField(buffer, table, 2)).containsExactly(100, 200, 300);
        assertThat(readBooleanField(buffer, table, 3)).isTrue();
        assertThat(readByteVectorField(buffer, table, 4)).containsExactly((byte) 10, (byte) 20, (byte) 30, (byte) 40);
        assertThat(readAll(builder.sizedInputStream())).isEqualTo(builder.sizedByteArray());
    }

    @Test
    void flatBufferBuilderCreatesVectorsOfNestedTables() {
        FlatBufferBuilder builder = new FlatBufferBuilder(64);
        int pencilOffset = createLineItem(builder, "pencil", 2, 125L);
        int notebookOffset = createLineItem(builder, "notebook", 1, 475L);
        int eraserOffset = createLineItem(builder, "eraser", 3, 50L);
        int itemsOffset = builder.createVectorOfTables(new int[] {pencilOffset, notebookOffset, eraserOffset});

        builder.startTable(1);
        builder.addOffset(0, itemsOffset, 0);
        int orderOffset = builder.endTable();
        builder.finish(orderOffset);

        ByteBuffer buffer = builder.dataBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int order = rootTable(buffer);
        int items = indirect(buffer, order + fieldOffset(buffer, order, 0));

        assertThat(buffer.getInt(items)).isEqualTo(3);
        assertLineItem(buffer, readTableVectorElement(buffer, items, 0), "pencil", 2, 125L);
        assertLineItem(buffer, readTableVectorElement(buffer, items, 1), "notebook", 1, 475L);
        assertLineItem(buffer, readTableVectorElement(buffer, items, 2), "eraser", 3, 50L);
    }

    @Test
    void flatBufferBuilderControlsDefaultFieldEmissionAndSizePrefixes() {
        FlatBufferBuilder defaultBuilder = new FlatBufferBuilder(8);
        defaultBuilder.startTable(1);
        defaultBuilder.addInt(0, 7, 7);
        int defaultTableOffset = defaultBuilder.endTable();
        defaultBuilder.finish(defaultTableOffset);
        ByteBuffer defaultBuffer = defaultBuilder.dataBuffer().order(ByteOrder.LITTLE_ENDIAN);
        assertThat(fieldOffset(defaultBuffer, rootTable(defaultBuffer), 0)).isZero();

        FlatBufferBuilder forcedBuilder = new FlatBufferBuilder(8).forceDefaults(true);
        forcedBuilder.startTable(1);
        forcedBuilder.addInt(0, 7, 7);
        int forcedTableOffset = forcedBuilder.endTable();
        forcedBuilder.finishSizePrefixed(forcedTableOffset, "DFLT");

        ByteBuffer prefixedBuffer = forcedBuilder.dataBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int sizePrefix = ByteBufferUtil.getSizePrefix(prefixedBuffer);
        ByteBuffer unprefixedBuffer = ByteBufferUtil.removeSizePrefix(prefixedBuffer).order(ByteOrder.LITTLE_ENDIAN);
        int table = rootTable(unprefixedBuffer);

        assertThat(sizePrefix).isEqualTo(unprefixedBuffer.remaining());
        assertThat(fileIdentifier(unprefixedBuffer)).isEqualTo("DFLT");
        assertThat(fieldOffset(unprefixedBuffer, table, 0)).isNotZero();
        assertThat(readIntField(unprefixedBuffer, table, 0)).isEqualTo(7);
    }

    @Test
    void flexBuffersRoundTripNestedMapsVectorsAndBlobs() {
        FlexBuffersBuilder builder = new FlexBuffersBuilder(
                new ArrayReadWriteBuf(256), FlexBuffersBuilder.BUILDER_FLAG_SHARE_ALL);
        int mapStart = builder.startMap();
        builder.putBoolean("active", true);
        builder.putInt("count", 3);
        builder.putString("name", "flatbuffers");
        builder.putBlob("payload", new byte[] {1, 1, 2, 3, 5, 8});

        int aliasesStart = builder.startVector();
        builder.putString("fast");
        builder.putString("compact");
        builder.endVector("aliases", aliasesStart, false, false);
        builder.endMap(null, mapStart);

        FlexBuffers.Map map = finishAndGetRoot(builder).asMap();
        FlexBuffers.Vector aliases = map.get("aliases").asVector();
        FlexBuffers.Blob payload = map.get("payload").asBlob();

        assertThat(map.size()).isEqualTo(5);
        assertThat(map.get("active").asBoolean()).isTrue();
        assertThat(map.get("count").asInt()).isEqualTo(3);
        assertThat(map.get("name").asString()).isEqualTo("flatbuffers");
        assertThat(aliases.size()).isEqualTo(2);
        assertThat(aliases.get(0).asString()).isEqualTo("fast");
        assertThat(aliases.get(1).asString()).isEqualTo("compact");
        assertThat(payload.getBytes()).containsExactly((byte) 1, (byte) 1, (byte) 2, (byte) 3, (byte) 5, (byte) 8);
        assertThat(map.keys().get(0).toString()).isEqualTo("active");
    }

    @Test
    void flexBuffersRoundTripScalarAndHomogeneousVectors() {
        FlexBuffersBuilder scalarBuilder = new FlexBuffersBuilder(
                new ArrayReadWriteBuf(64), FlexBuffersBuilder.BUILDER_FLAG_NONE);
        scalarBuilder.putFloat(12.5d);
        FlexBuffers.Reference scalar = finishAndGetRoot(scalarBuilder);
        assertThat(scalar.isFloat()).isTrue();
        assertThat(scalar.asFloat()).isEqualTo(12.5d);

        FlexBuffersBuilder vectorBuilder = new FlexBuffersBuilder(
                new ArrayReadWriteBuf(128), FlexBuffersBuilder.BUILDER_FLAG_NONE);
        int vectorStart = vectorBuilder.startVector();
        vectorBuilder.putFloat(1.25d);
        vectorBuilder.putFloat(2.5d);
        vectorBuilder.putFloat(5.0d);
        vectorBuilder.endVector(null, vectorStart, false, false);

        FlexBuffers.Reference vectorReference = finishAndGetRoot(vectorBuilder);
        FlexBuffers.Vector vector = vectorReference.asVector();
        assertThat(vectorReference.isVector()).isTrue();
        assertThat(vector.size()).isEqualTo(3);
        assertThat(vector.get(0).asFloat()).isEqualTo(1.25d);
        assertThat(vector.get(1).asFloat()).isEqualTo(2.5d);
        assertThat(vector.get(2).asFloat()).isEqualTo(5.0d);
    }

    @Test
    void flexBuffersRoundTripTypedVectors() {
        FlexBuffersBuilder builder = new FlexBuffersBuilder(
                new ArrayReadWriteBuf(128), FlexBuffersBuilder.BUILDER_FLAG_NONE);
        int vectorStart = builder.startVector();
        builder.putInt(7);
        builder.putInt(14);
        builder.putInt(28);
        builder.putInt(56);
        builder.endVector(null, vectorStart, true, false);

        FlexBuffers.Reference reference = finishAndGetRoot(builder);
        FlexBuffers.TypedVector vector = (FlexBuffers.TypedVector) reference.asVector();

        assertThat(reference.isTypedVector()).isTrue();
        assertThat(reference.getType()).isEqualTo(FlexBuffers.FBT_VECTOR_INT);
        assertThat(vector.getElemType()).isEqualTo(FlexBuffers.FBT_INT);
        assertThat(vector.isEmptyVector()).isFalse();
        assertThat(vector.size()).isEqualTo(4);
        assertThat(vector.get(0).isInt()).isTrue();
        assertThat(vector.get(0).asInt()).isEqualTo(7);
        assertThat(vector.get(1).asInt()).isEqualTo(14);
        assertThat(vector.get(2).asInt()).isEqualTo(28);
        assertThat(vector.get(3).asInt()).isEqualTo(56);
    }

    @Test
    void readWriteBuffersSupportSequentialWritesRandomAccessUpdatesAndUtf8Strings() {
        ArrayReadWriteBuf arrayBuffer = new ArrayReadWriteBuf(64);
        arrayBuffer.putBoolean(true);
        arrayBuffer.putInt(0x01020304);
        arrayBuffer.putLong(0x0102030405060708L);
        arrayBuffer.putFloat(3.5f);
        arrayBuffer.putDouble(7.25d);
        arrayBuffer.setInt(1, 0x0A0B0C0D);
        arrayBuffer.set(5, new byte[] {'h', 'e', 'l', 'l', 'o'}, 1, 3);

        assertThat(arrayBuffer.getBoolean(0)).isTrue();
        assertThat(arrayBuffer.getInt(1)).isEqualTo(0x0A0B0C0D);
        assertThat(arrayBuffer.getString(5, 3)).isEqualTo("ell");
        assertThat(arrayBuffer.writePosition()).isGreaterThan(Short.BYTES);
        assertThat(arrayBuffer.limit()).isGreaterThanOrEqualTo(arrayBuffer.writePosition());

        ByteBufferReadWriteBuf byteBuffer = new ByteBufferReadWriteBuf(ByteBuffer.allocate(64));
        byteBuffer.putShort((short) 1234);
        byteBuffer.put(new byte[] {9, 8, 7}, 0, 3);
        byteBuffer.setLong(8, 123456789L);
        byteBuffer.setDouble(24, 0.125d);

        assertThat(byteBuffer.getShort(0)).isEqualTo((short) 1234);
        assertThat(byteBuffer.get(2)).isEqualTo((byte) 9);
        assertThat(byteBuffer.get(4)).isEqualTo((byte) 7);
        assertThat(byteBuffer.getLong(8)).isEqualTo(123456789L);
        assertThat(byteBuffer.getDouble(24)).isEqualTo(0.125d);
        assertThat(byteBuffer.requestCapacity(63)).isTrue();
    }

    private static int createLineItem(FlatBufferBuilder builder, String name, int quantity, long unitPriceInCents) {
        int nameOffset = builder.createString(name);
        builder.startTable(3);
        builder.addOffset(0, nameOffset, 0);
        builder.addInt(1, quantity, 0);
        builder.addLong(2, unitPriceInCents, 0L);
        return builder.endTable();
    }

    private static void assertLineItem(ByteBuffer buffer, int item, String name, int quantity, long unitPriceInCents) {
        assertThat(readStringField(buffer, item, 0)).isEqualTo(name);
        assertThat(readIntField(buffer, item, 1)).isEqualTo(quantity);
        assertThat(readLongField(buffer, item, 2)).isEqualTo(unitPriceInCents);
    }

    private static FlexBuffers.Reference finishAndGetRoot(FlexBuffersBuilder builder) {
        builder.finish();
        return FlexBuffers.getRoot(builder.getBuffer());
    }

    private static int rootTable(ByteBuffer buffer) {
        int position = buffer.position();
        return position + buffer.getInt(position);
    }

    private static String fileIdentifier(ByteBuffer buffer) {
        int identifierStart = buffer.position() + Integer.BYTES;
        byte[] identifier = new byte[4];
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.position(identifierStart);
        duplicate.get(identifier);
        return new String(identifier, StandardCharsets.US_ASCII);
    }

    private static int fieldOffset(ByteBuffer buffer, int table, int field) {
        int vtable = table - buffer.getInt(table);
        int vtableLength = Short.toUnsignedInt(buffer.getShort(vtable));
        int fieldEntry = VTABLE_HEADER_SIZE + field * FIELD_WIDTH;
        if (fieldEntry >= vtableLength) {
            return 0;
        }
        return Short.toUnsignedInt(buffer.getShort(vtable + fieldEntry));
    }

    private static int readIntField(ByteBuffer buffer, int table, int field) {
        return buffer.getInt(table + fieldOffset(buffer, table, field));
    }

    private static long readLongField(ByteBuffer buffer, int table, int field) {
        return buffer.getLong(table + fieldOffset(buffer, table, field));
    }

    private static boolean readBooleanField(ByteBuffer buffer, int table, int field) {
        return buffer.get(table + fieldOffset(buffer, table, field)) != 0;
    }

    private static String readStringField(ByteBuffer buffer, int table, int field) {
        int stringStart = indirect(buffer, table + fieldOffset(buffer, table, field));
        int stringLength = buffer.getInt(stringStart);
        byte[] bytes = new byte[stringLength];
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.position(stringStart + Integer.BYTES);
        duplicate.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readTableVectorElement(ByteBuffer buffer, int vector, int index) {
        int offsetLocation = vector + Integer.BYTES + index * Integer.BYTES;
        return indirect(buffer, offsetLocation);
    }

    private static int[] readIntVectorField(ByteBuffer buffer, int table, int field) {
        int vectorStart = indirect(buffer, table + fieldOffset(buffer, table, field));
        int length = buffer.getInt(vectorStart);
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = buffer.getInt(vectorStart + Integer.BYTES + i * Integer.BYTES);
        }
        return values;
    }

    private static byte[] readByteVectorField(ByteBuffer buffer, int table, int field) {
        int vectorStart = indirect(buffer, table + fieldOffset(buffer, table, field));
        int length = buffer.getInt(vectorStart);
        byte[] values = new byte[length];
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.position(vectorStart + Integer.BYTES);
        duplicate.get(values);
        return values;
    }

    private static int indirect(ByteBuffer buffer, int offsetLocation) {
        return offsetLocation + buffer.getInt(offsetLocation);
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[32];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }
}
