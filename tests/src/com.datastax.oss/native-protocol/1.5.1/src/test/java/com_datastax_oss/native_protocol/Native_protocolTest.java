/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_datastax_oss.native_protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.dse.protocol.internal.DseProtocolConstants;
import com.datastax.dse.protocol.internal.DseProtocolV2ClientCodecs;
import com.datastax.dse.protocol.internal.DseProtocolV2ServerCodecs;
import com.datastax.dse.protocol.internal.request.query.ContinuousPagingOptions;
import com.datastax.dse.protocol.internal.request.query.DseQueryOptions;
import com.datastax.oss.protocol.internal.Compressor;
import com.datastax.oss.protocol.internal.Frame;
import com.datastax.oss.protocol.internal.FrameCodec;
import com.datastax.oss.protocol.internal.PrimitiveCodec;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import com.datastax.oss.protocol.internal.request.Batch;
import com.datastax.oss.protocol.internal.request.Execute;
import com.datastax.oss.protocol.internal.request.Prepare;
import com.datastax.oss.protocol.internal.request.Query;
import com.datastax.oss.protocol.internal.request.query.QueryOptions;
import com.datastax.oss.protocol.internal.response.result.ColumnSpec;
import com.datastax.oss.protocol.internal.response.result.DefaultRows;
import com.datastax.oss.protocol.internal.response.result.Prepared;
import com.datastax.oss.protocol.internal.response.result.RawType;
import com.datastax.oss.protocol.internal.response.result.Rows;
import com.datastax.oss.protocol.internal.response.result.RowsMetadata;
import com.datastax.oss.protocol.internal.util.Bytes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

public class Native_protocolTest {
    private static final ByteBufferPrimitiveCodec PRIMITIVE_CODEC = new ByteBufferPrimitiveCodec();

    @Test
    void clientQueryFrameRoundTripsThroughServerCodec() {
        FrameCodec<ByteBuffer> clientCodec = FrameCodec.defaultClient(PRIMITIVE_CODEC, Compressor.none());
        FrameCodec<ByteBuffer> serverCodec = FrameCodec.defaultServer(PRIMITIVE_CODEC, Compressor.none());

        Map<String, ByteBuffer> namedValues = new LinkedHashMap<>();
        namedValues.put("login", bytes("alice"));
        namedValues.put("optional", null);
        namedValues.put("unset", ProtocolConstants.UNSET_VALUE);

        QueryOptions options = new QueryOptions(
                ProtocolConstants.ConsistencyLevel.LOCAL_QUORUM,
                List.of(),
                namedValues,
                true,
                128,
                bytes(0x01, 0x02, 0x03),
                ProtocolConstants.ConsistencyLevel.LOCAL_SERIAL,
                1_701_234_567_890L,
                "ks1",
                42);
        Query query = new Query("SELECT * FROM users WHERE login = :login", options);
        Map<String, ByteBuffer> customPayload = Map.of("driver", bytes("metadata"));

        ByteBuffer encoded = flip(clientCodec.encode(Frame.forRequest(
                ProtocolConstants.Version.V5,
                37,
                true,
                customPayload,
                query)));

        assertThat(clientCodec.decodeBodySize(encoded.duplicate()))
                .isEqualTo(encoded.remaining() - FrameCodec.V3_ENCODED_HEADER_SIZE);

        Frame decodedFrame = serverCodec.decode(encoded);
        assertThat(decodedFrame.protocolVersion).isEqualTo(ProtocolConstants.Version.V5);
        assertThat(decodedFrame.streamId).isEqualTo(37);
        assertThat(decodedFrame.tracing).isTrue();
        assertThat(decodedFrame.message).isInstanceOf(Query.class);
        assertBufferEquals(decodedFrame.customPayload.get("driver"), bytes("metadata"));

        Query decodedQuery = (Query) decodedFrame.message;
        assertThat(decodedQuery.query).isEqualTo("SELECT * FROM users WHERE login = :login");
        assertThat(decodedQuery.options.consistency).isEqualTo(ProtocolConstants.ConsistencyLevel.LOCAL_QUORUM);
        assertThat(decodedQuery.options.skipMetadata).isTrue();
        assertThat(decodedQuery.options.pageSize).isEqualTo(128);
        assertBufferEquals(decodedQuery.options.pagingState, bytes(0x01, 0x02, 0x03));
        assertThat(decodedQuery.options.serialConsistency).isEqualTo(ProtocolConstants.ConsistencyLevel.LOCAL_SERIAL);
        assertThat(decodedQuery.options.defaultTimestamp).isEqualTo(1_701_234_567_890L);
        assertThat(decodedQuery.options.keyspace).isEqualTo("ks1");
        assertThat(decodedQuery.options.nowInSeconds).isEqualTo(42);
        assertBufferEquals(decodedQuery.options.namedValues.get("login"), bytes("alice"));
        assertThat(decodedQuery.options.namedValues.get("optional")).isNull();
        assertThat(decodedQuery.options.namedValues.get("unset")).isSameAs(ProtocolConstants.UNSET_VALUE);
    }

    @Test
    void serverRowsResponsePreservesMetadataRowsTracingWarningsAndPayload() {
        FrameCodec<ByteBuffer> clientCodec = FrameCodec.defaultClient(PRIMITIVE_CODEC, Compressor.none());
        FrameCodec<ByteBuffer> serverCodec = FrameCodec.defaultServer(PRIMITIVE_CODEC, Compressor.none());
        RawType intType = RawType.PRIMITIVES.get(ProtocolConstants.DataType.INT);
        RawType textType = RawType.PRIMITIVES.get(ProtocolConstants.DataType.VARCHAR);
        RawType attributesType = new RawType.RawMap(textType, textType);
        List<ColumnSpec> columns = List.of(
                new ColumnSpec("ks1", "users", "id", 0, intType),
                new ColumnSpec("ks1", "users", "attributes", 1, attributesType));
        RowsMetadata metadata = new RowsMetadata(columns, bytes(0x55, 0x66), new int[] {0}, new byte[] {0x01, 0x02});
        Queue<List<ByteBuffer>> data = new ArrayDeque<>();
        data.add(List.of(bytes(0, 0, 0, 1), bytes("role=admin")));
        data.add(List.of(bytes(0, 0, 0, 2), bytes("role=user")));
        UUID tracingId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        Map<String, ByteBuffer> customPayload = Map.of("server", bytes("coordinator-1"));

        ByteBuffer encoded = flip(serverCodec.encode(Frame.forResponse(
                ProtocolConstants.Version.V5,
                -11,
                tracingId,
                customPayload,
                List.of("using experimental protocol"),
                new DefaultRows(metadata, data))));

        Frame decodedFrame = clientCodec.decode(encoded);
        assertThat(decodedFrame.protocolVersion).isEqualTo(ProtocolConstants.Version.V5);
        assertThat(decodedFrame.streamId).isEqualTo(-11);
        assertThat(decodedFrame.tracingId).isEqualTo(tracingId);
        assertThat(decodedFrame.warnings).containsExactly("using experimental protocol");
        assertBufferEquals(decodedFrame.customPayload.get("server"), bytes("coordinator-1"));
        assertThat(decodedFrame.message).isInstanceOf(Rows.class);

        Rows decodedRows = (Rows) decodedFrame.message;
        assertThat(decodedRows.toString()).isEqualTo("ROWS(2 x 2 columns)");
        assertThat(decodedRows.getMetadata().columnSpecs).containsExactlyElementsOf(columns);
        assertThat(decodedRows.getMetadata().columnCount).isEqualTo(2);
        assertBufferEquals(decodedRows.getMetadata().pagingState, bytes(0x55, 0x66));
        assertThat(decodedRows.getMetadata().newResultMetadataId).containsExactly((byte) 0x01, (byte) 0x02);

        List<List<ByteBuffer>> decodedData = new ArrayList<>(decodedRows.getData());
        assertThat(decodedData).hasSize(2);
        assertBufferEquals(decodedData.get(0).get(0), bytes(0, 0, 0, 1));
        assertBufferEquals(decodedData.get(0).get(1), bytes("role=admin"));
        assertBufferEquals(decodedData.get(1).get(0), bytes(0, 0, 0, 2));
        assertBufferEquals(decodedData.get(1).get(1), bytes("role=user"));
    }

    @Test
    void rawTypesAndUtilityBytesSupportNestedProtocolTypes() {
        RawType intType = RawType.PRIMITIVES.get(ProtocolConstants.DataType.INT);
        RawType textType = RawType.PRIMITIVES.get(ProtocolConstants.DataType.VARCHAR);
        RawType nestedType = new RawType.RawTuple(List.of(
                new RawType.RawList(intType),
                new RawType.RawUdt("ks1", "address", Map.of(
                        "street", textType,
                        "zip", intType)),
                new RawType.RawCustom("com.example.CustomType")));

        ByteBuffer encoded = PRIMITIVE_CODEC.allocate(nestedType.encodedSize(ProtocolConstants.Version.V5));
        nestedType.encode(encoded, PRIMITIVE_CODEC, ProtocolConstants.Version.V5);
        RawType decodedType = RawType.decode(flip(encoded), PRIMITIVE_CODEC, ProtocolConstants.Version.V5);

        assertThat(decodedType).isEqualTo(nestedType);
        ByteBuffer fromHex = Bytes.fromHexString("0x0001020aff");
        assertThat(Bytes.toHexString(fromHex.duplicate())).isEqualTo("0x0001020aff");
        byte[] copied = Bytes.getArray(fromHex.duplicate());
        assertThat(copied).containsExactly((byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x0a, (byte) 0xff);
        Bytes.erase(fromHex);
        fromHex.rewind();
        assertThat(Bytes.getArray(fromHex.duplicate())).containsOnly((byte) 0);
    }

    @Test
    void preparedStatementMessagesRoundTripBetweenClientAndServer() {
        FrameCodec<ByteBuffer> clientCodec = FrameCodec.defaultClient(PRIMITIVE_CODEC, Compressor.none());
        FrameCodec<ByteBuffer> serverCodec = FrameCodec.defaultServer(PRIMITIVE_CODEC, Compressor.none());

        ByteBuffer encodedPrepare = flip(clientCodec.encode(Frame.forRequest(
                ProtocolConstants.Version.V5,
                12,
                false,
                Frame.NO_PAYLOAD,
                new Prepare("SELECT * FROM users WHERE id = ?", "app"))));

        Frame decodedPrepareFrame = serverCodec.decode(encodedPrepare);
        assertThat(decodedPrepareFrame.streamId).isEqualTo(12);
        assertThat(decodedPrepareFrame.message).isInstanceOf(Prepare.class);
        Prepare decodedPrepare = (Prepare) decodedPrepareFrame.message;
        assertThat(decodedPrepare.cqlQuery).isEqualTo("SELECT * FROM users WHERE id = ?");
        assertThat(decodedPrepare.keyspace).isEqualTo("app");

        RawType uuidType = RawType.PRIMITIVES.get(ProtocolConstants.DataType.UUID);
        RawType textType = RawType.PRIMITIVES.get(ProtocolConstants.DataType.VARCHAR);
        ColumnSpec idVariable = new ColumnSpec("app", "users", "id", 0, uuidType);
        RowsMetadata variablesMetadata = new RowsMetadata(List.of(idVariable), null, new int[] {0}, null);
        List<ColumnSpec> resultColumns = List.of(
                new ColumnSpec("app", "users", "id", 0, uuidType),
                new ColumnSpec("app", "users", "login", 1, textType));
        RowsMetadata resultMetadata = new RowsMetadata(resultColumns, null, new int[] {0}, new byte[] {0x03, 0x04});
        byte[] preparedQueryId = new byte[] {0x11, 0x22, 0x33};
        byte[] resultMetadataId = new byte[] {0x44, 0x55};

        ByteBuffer encodedPrepared = flip(serverCodec.encode(Frame.forResponse(
                ProtocolConstants.Version.V5,
                12,
                null,
                Frame.NO_PAYLOAD,
                List.of(),
                new Prepared(preparedQueryId, resultMetadataId, variablesMetadata, resultMetadata))));

        Frame decodedPreparedFrame = clientCodec.decode(encodedPrepared);
        assertThat(decodedPreparedFrame.message).isInstanceOf(Prepared.class);
        Prepared decodedPrepared = (Prepared) decodedPreparedFrame.message;
        assertThat(decodedPrepared.preparedQueryId).containsExactly(preparedQueryId);
        assertThat(decodedPrepared.resultMetadataId).containsExactly(resultMetadataId);
        assertThat(decodedPrepared.variablesMetadata.columnSpecs).containsExactly(idVariable);
        assertThat(decodedPrepared.variablesMetadata.pkIndices).containsExactly(0);
        assertThat(decodedPrepared.resultMetadata.columnSpecs).containsExactlyElementsOf(resultColumns);
        assertThat(decodedPrepared.resultMetadata.newResultMetadataId).containsExactly((byte) 0x03, (byte) 0x04);

        QueryOptions executeOptions = new QueryOptions(
                ProtocolConstants.ConsistencyLevel.QUORUM,
                List.of(bytes(0xaa, 0xbb, 0xcc, 0xdd)),
                Map.of(),
                false,
                -1,
                null,
                ProtocolConstants.ConsistencyLevel.SERIAL,
                QueryOptions.NO_DEFAULT_TIMESTAMP,
                "app",
                QueryOptions.NO_NOW_IN_SECONDS);
        ByteBuffer encodedExecute = flip(clientCodec.encode(Frame.forRequest(
                ProtocolConstants.Version.V5,
                13,
                false,
                Frame.NO_PAYLOAD,
                new Execute(preparedQueryId, resultMetadataId, executeOptions))));

        Frame decodedExecuteFrame = serverCodec.decode(encodedExecute);
        assertThat(decodedExecuteFrame.streamId).isEqualTo(13);
        assertThat(decodedExecuteFrame.message).isInstanceOf(Execute.class);
        Execute decodedExecute = (Execute) decodedExecuteFrame.message;
        assertThat(decodedExecute.queryId).containsExactly(preparedQueryId);
        assertThat(decodedExecute.resultMetadataId).containsExactly(resultMetadataId);
        assertThat(decodedExecute.options.consistency).isEqualTo(ProtocolConstants.ConsistencyLevel.QUORUM);
        assertBufferEquals(decodedExecute.options.positionalValues.get(0), bytes(0xaa, 0xbb, 0xcc, 0xdd));
        assertThat(decodedExecute.options.keyspace).isEqualTo("app");
    }

    @Test
    void batchRequestRoundTripsStatementsPreparedIdsValuesAndExecutionOptions() {
        FrameCodec<ByteBuffer> clientCodec = FrameCodec.defaultClient(PRIMITIVE_CODEC, Compressor.none());
        FrameCodec<ByteBuffer> serverCodec = FrameCodec.defaultServer(PRIMITIVE_CODEC, Compressor.none());
        byte[] preparedQueryId = new byte[] {0x01, 0x23, 0x45};
        List<Object> queriesOrIds = List.of(
                "INSERT INTO users (id, login) VALUES (?, ?)",
                preparedQueryId);
        List<List<ByteBuffer>> values = List.of(
                List.of(bytes(0, 0, 0, 7), bytes("carol")),
                List.of(bytes("last-login")));

        ByteBuffer encoded = flip(clientCodec.encode(Frame.forRequest(
                ProtocolConstants.Version.V5,
                44,
                false,
                Frame.NO_PAYLOAD,
                new Batch(
                        ProtocolConstants.BatchType.UNLOGGED,
                        queriesOrIds,
                        values,
                        ProtocolConstants.ConsistencyLevel.LOCAL_QUORUM,
                        ProtocolConstants.ConsistencyLevel.SERIAL,
                        1_702_345_678_901L,
                        "app",
                        123))));

        Frame decodedFrame = serverCodec.decode(encoded);
        assertThat(decodedFrame.streamId).isEqualTo(44);
        assertThat(decodedFrame.message).isInstanceOf(Batch.class);
        Batch decodedBatch = (Batch) decodedFrame.message;
        assertThat(decodedBatch.type).isEqualTo(ProtocolConstants.BatchType.UNLOGGED);
        assertThat(decodedBatch.queriesOrIds.get(0)).isEqualTo("INSERT INTO users (id, login) VALUES (?, ?)");
        assertThat((byte[]) decodedBatch.queriesOrIds.get(1)).containsExactly(preparedQueryId);
        assertBufferEquals(decodedBatch.values.get(0).get(0), bytes(0, 0, 0, 7));
        assertBufferEquals(decodedBatch.values.get(0).get(1), bytes("carol"));
        assertBufferEquals(decodedBatch.values.get(1).get(0), bytes("last-login"));
        assertThat(decodedBatch.consistency).isEqualTo(ProtocolConstants.ConsistencyLevel.LOCAL_QUORUM);
        assertThat(decodedBatch.serialConsistency).isEqualTo(ProtocolConstants.ConsistencyLevel.SERIAL);
        assertThat(decodedBatch.defaultTimestamp).isEqualTo(1_702_345_678_901L);
        assertThat(decodedBatch.keyspace).isEqualTo("app");
        assertThat(decodedBatch.nowInSeconds).isEqualTo(123);
    }

    @Test
    void dseV2QueryOptionsRoundTripContinuousPagingSettings() {
        FrameCodec<ByteBuffer> dseClientCodec = new FrameCodec<>(
                PRIMITIVE_CODEC,
                Compressor.none(),
                new DseProtocolV2ClientCodecs());
        FrameCodec<ByteBuffer> dseServerCodec = new FrameCodec<>(
                PRIMITIVE_CODEC,
                Compressor.none(),
                new DseProtocolV2ServerCodecs());
        DseQueryOptions options = new DseQueryOptions(
                ProtocolConstants.ConsistencyLevel.LOCAL_ONE,
                List.of(bytes("graph-source")),
                Map.of(),
                false,
                4_096,
                bytes(0x10, 0x20),
                ProtocolConstants.ConsistencyLevel.SERIAL,
                99L,
                "graph_ks",
                true,
                new ContinuousPagingOptions(8, 16, 4));

        ByteBuffer encoded = flip(dseClientCodec.encode(Frame.forRequest(
                DseProtocolConstants.Version.DSE_V2,
                5,
                false,
                Frame.NO_PAYLOAD,
                new Query("g.V().has('name', ?)", options))));

        Frame decodedFrame = dseServerCodec.decode(encoded);
        assertThat(decodedFrame.protocolVersion).isEqualTo(DseProtocolConstants.Version.DSE_V2);
        assertThat(decodedFrame.message).isInstanceOf(Query.class);
        Query decodedQuery = (Query) decodedFrame.message;
        assertThat(decodedQuery.query).isEqualTo("g.V().has('name', ?)");
        assertThat(decodedQuery.options).isInstanceOf(DseQueryOptions.class);

        DseQueryOptions decodedOptions = (DseQueryOptions) decodedQuery.options;
        assertThat(decodedOptions.consistency).isEqualTo(ProtocolConstants.ConsistencyLevel.LOCAL_ONE);
        assertBufferEquals(decodedOptions.positionalValues.get(0), bytes("graph-source"));
        assertThat(decodedOptions.pageSize).isEqualTo(4_096);
        assertBufferEquals(decodedOptions.pagingState, bytes(0x10, 0x20));
        assertThat(decodedOptions.defaultTimestamp).isEqualTo(99L);
        assertThat(decodedOptions.keyspace).isEqualTo("graph_ks");
        assertThat(decodedOptions.isPageSizeInBytes).isTrue();
        assertThat(decodedOptions.continuousPagingOptions.maxPages).isEqualTo(8);
        assertThat(decodedOptions.continuousPagingOptions.pagesPerSecond).isEqualTo(16);
        assertThat(decodedOptions.continuousPagingOptions.nextPages).isEqualTo(4);
    }

    private static ByteBuffer bytes(String value) {
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ByteBuffer bytes(int... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = (byte) values[i];
        }
        return ByteBuffer.wrap(data);
    }

    private static ByteBuffer flip(ByteBuffer buffer) {
        buffer.flip();
        return buffer;
    }

    private static void assertBufferEquals(ByteBuffer actual, ByteBuffer expected) {
        assertThat(toByteArray(actual)).containsExactly(toByteArray(expected));
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] data = new byte[duplicate.remaining()];
        duplicate.get(data);
        return data;
    }

    private static final class ByteBufferPrimitiveCodec implements PrimitiveCodec<ByteBuffer> {
        @Override
        public ByteBuffer allocate(int size) {
            return ByteBuffer.allocate(size);
        }

        @Override
        public void release(ByteBuffer buffer) {
            // Heap buffers do not retain external resources.
        }

        @Override
        public int sizeOf(ByteBuffer buffer) {
            return buffer.remaining();
        }

        @Override
        public ByteBuffer concat(ByteBuffer left, ByteBuffer right) {
            ByteBuffer leftSource = prepareForReading(left);
            ByteBuffer rightSource = prepareForReading(right);
            ByteBuffer concatenated = allocate(leftSource.remaining() + rightSource.remaining());
            concatenated.put(leftSource);
            concatenated.put(rightSource);
            return concatenated;
        }

        @Override
        public void markReaderIndex(ByteBuffer source) {
            source.mark();
        }

        @Override
        public void resetReaderIndex(ByteBuffer source) {
            source.reset();
        }

        @Override
        public byte readByte(ByteBuffer source) {
            return source.get();
        }

        @Override
        public int readInt(ByteBuffer source) {
            return source.getInt();
        }

        @Override
        public int readInt(ByteBuffer source, int index) {
            return source.getInt(index);
        }

        @Override
        public InetAddress readInetAddr(ByteBuffer source) {
            byte[] address = new byte[Byte.toUnsignedInt(source.get())];
            source.get(address);
            try {
                return InetAddress.getByAddress(address);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid encoded address", e);
            }
        }

        @Override
        public long readLong(ByteBuffer source) {
            return source.getLong();
        }

        @Override
        public int readUnsignedShort(ByteBuffer source) {
            return Short.toUnsignedInt(source.getShort());
        }

        @Override
        public ByteBuffer readBytes(ByteBuffer source) {
            int length = source.getInt();
            if (length == -1) {
                return null;
            }
            if (length == -2) {
                return ProtocolConstants.UNSET_VALUE;
            }
            return readFixedBytes(source, length);
        }

        @Override
        public byte[] readShortBytes(ByteBuffer source) {
            int length = readUnsignedShort(source);
            byte[] bytes = new byte[length];
            source.get(bytes);
            return bytes;
        }

        @Override
        public String readString(ByteBuffer source) {
            return readUtf8(source, readUnsignedShort(source));
        }

        @Override
        public String readLongString(ByteBuffer source) {
            return readUtf8(source, source.getInt());
        }

        @Override
        public ByteBuffer readRetainedSlice(ByteBuffer source, int length) {
            return readFixedBytes(source, length);
        }

        @Override
        public void updateCrc(ByteBuffer source, CRC32 crc) {
            crc.update(source.duplicate());
        }

        @Override
        public void writeByte(byte value, ByteBuffer destination) {
            destination.put(value);
        }

        @Override
        public void writeInt(int value, ByteBuffer destination) {
            destination.putInt(value);
        }

        @Override
        public void writeInetAddr(InetAddress address, ByteBuffer destination) {
            byte[] bytes = address.getAddress();
            destination.put((byte) bytes.length);
            destination.put(bytes);
        }

        @Override
        public void writeLong(long value, ByteBuffer destination) {
            destination.putLong(value);
        }

        @Override
        public void writeUnsignedShort(int value, ByteBuffer destination) {
            destination.putShort((short) value);
        }

        @Override
        public void writeString(String value, ByteBuffer destination) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeUnsignedShort(bytes.length, destination);
            destination.put(bytes);
        }

        @Override
        public void writeLongString(String value, ByteBuffer destination) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            destination.putInt(bytes.length);
            destination.put(bytes);
        }

        @Override
        public void writeBytes(ByteBuffer value, ByteBuffer destination) {
            if (value == null) {
                destination.putInt(-1);
            } else {
                ByteBuffer duplicate = value.duplicate();
                destination.putInt(duplicate.remaining());
                destination.put(duplicate);
            }
        }

        @Override
        public void writeBytes(byte[] value, ByteBuffer destination) {
            if (value == null) {
                destination.putInt(-1);
            } else {
                destination.putInt(value.length);
                destination.put(value);
            }
        }

        @Override
        public void writeShortBytes(byte[] value, ByteBuffer destination) {
            writeUnsignedShort(value.length, destination);
            destination.put(value);
        }

        @Override
        public InetSocketAddress readInet(ByteBuffer source) {
            InetAddress address = readInetAddr(source);
            int port = source.getInt();
            return new InetSocketAddress(address, port);
        }

        @Override
        public void writeInet(InetSocketAddress address, ByteBuffer destination) {
            writeInetAddr(address.getAddress(), destination);
            destination.putInt(address.getPort());
        }

        private ByteBuffer readFixedBytes(ByteBuffer source, int length) {
            byte[] bytes = new byte[length];
            source.get(bytes);
            return ByteBuffer.wrap(bytes);
        }

        private String readUtf8(ByteBuffer source, int length) {
            byte[] bytes = new byte[length];
            source.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private ByteBuffer prepareForReading(ByteBuffer buffer) {
            ByteBuffer duplicate = buffer.duplicate();
            if (duplicate.position() != 0 && duplicate.limit() == duplicate.capacity()) {
                duplicate.flip();
            }
            return duplicate;
        }
    }
}
