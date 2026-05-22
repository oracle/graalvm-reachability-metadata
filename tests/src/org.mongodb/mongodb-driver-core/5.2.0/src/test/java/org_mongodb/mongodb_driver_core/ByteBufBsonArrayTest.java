/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongodb_driver_core;

import com.mongodb.LoggerSettings;
import com.mongodb.MongoDriverInformation;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.connection.AsyncCompletionHandler;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.ServerSettings;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.internal.TimeoutSettings;
import com.mongodb.internal.binding.ClusterBinding;
import com.mongodb.internal.connection.Cluster;
import com.mongodb.internal.connection.DefaultClusterFactory;
import com.mongodb.internal.connection.InternalConnectionPoolSettings;
import com.mongodb.internal.connection.OperationContext;
import com.mongodb.internal.connection.PowerOfTwoBufferPool;
import com.mongodb.internal.connection.Stream;
import com.mongodb.internal.connection.StreamFactory;
import com.mongodb.internal.operation.CommandReadOperation;
import org.bson.BsonArray;
import org.bson.BsonBinaryWriter;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.ByteBuf;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ByteBufBsonArrayTest {
    private static final int MESSAGE_HEADER_LENGTH = 16;
    private static final int OP_REPLY = 1;
    private static final int OP_QUERY = 2004;
    private static final int OP_MSG = 2013;

    @Test
    void commandStartedEventConvertsByteBufferedArrayToTypedArray() {
        final ServerAddress serverAddress = new ServerAddress("127.0.0.1", 27017);
        final RecordingCommandListener commandListener = new RecordingCommandListener();
        final RespondingStreamFactory streamFactory = new RespondingStreamFactory(serverAddress);
        final TimeoutSettings timeoutSettings = new TimeoutSettings(5_000, 500, 5_000, null, 5_000);
        final OperationContext operationContext = OperationContext.simpleOperationContext(timeoutSettings, null);
        final Cluster cluster = new DefaultClusterFactory().createCluster(
                ClusterSettings.builder()
                        .hosts(Collections.singletonList(serverAddress))
                        .mode(ClusterConnectionMode.SINGLE)
                        .serverSelectionTimeout(5, TimeUnit.SECONDS)
                        .build(),
                ServerSettings.builder()
                        .heartbeatFrequency(1, TimeUnit.HOURS)
                        .minHeartbeatFrequency(1, TimeUnit.HOURS)
                        .build(),
                ConnectionPoolSettings.builder()
                        .maxSize(1)
                        .build(),
                InternalConnectionPoolSettings.builder().build(),
                timeoutSettings,
                streamFactory,
                timeoutSettings,
                streamFactory,
                null,
                LoggerSettings.builder().build(),
                commandListener,
                "byte-buf-bson-array-test",
                MongoDriverInformation.builder().build(),
                Collections.emptyList(),
                null,
                null);
        final ClusterBinding binding = new ClusterBinding(cluster, ReadPreference.primary(), ReadConcern.DEFAULT,
                operationContext);
        try {
            final BsonDocument matchStage = new BsonDocument("$match",
                    new BsonDocument("status", new BsonString("active")));
            final BsonDocument limitStage = new BsonDocument("$limit", new BsonInt32(5));
            final BsonDocument command = new BsonDocument("aggregate", new BsonString("widgets"))
                    .append("pipeline", new BsonArray(Arrays.asList(matchStage, limitStage)))
                    .append("cursor", new BsonDocument());
            final CommandReadOperation<BsonDocument> operation = new CommandReadOperation<>("test", command,
                    new BsonDocumentCodec());
            final BsonDocument result = operation.execute(binding);

            assertThat(result.getNumber("ok").doubleValue()).isEqualTo(1.0);
            assertThat(commandListener.pipelineValues).containsExactly(matchStage, limitStage);
            assertThat(commandListener.pipelineArrayClassName)
                    .isEqualTo("com.mongodb.internal.connection.ByteBufBsonArray");
        } finally {
            binding.release();
            cluster.close();
        }
    }

    private static final class RecordingCommandListener implements CommandListener {
        private List<BsonValue> pipelineValues = Collections.emptyList();
        private String pipelineArrayClassName;

        @Override
        public void commandStarted(final CommandStartedEvent event) {
            if (!"aggregate".equals(event.getCommandName())) {
                return;
            }
            final BsonArray pipeline = event.getCommand().getArray("pipeline");
            pipelineArrayClassName = pipeline.getClass().getName();
            pipelineValues = Arrays.asList(pipeline.toArray(new BsonValue[0]));
        }
    }

    private static final class RespondingStreamFactory implements StreamFactory {
        private final ServerAddress serverAddress;

        private RespondingStreamFactory(final ServerAddress serverAddress) {
            this.serverAddress = serverAddress;
        }

        @Override
        public Stream create(final ServerAddress address) {
            return new RespondingStream(serverAddress);
        }
    }

    private static final class RespondingStream implements Stream {
        private final ServerAddress serverAddress;
        private byte[] nextResponse;
        private int nextResponsePosition;
        private int lastRequestId;
        private int lastOpCode;
        private int responseRequestId = 1;
        private int responsesCreated;
        private boolean closed;

        private RespondingStream(final ServerAddress serverAddress) {
            this.serverAddress = serverAddress;
        }

        @Override
        public void open(final OperationContext operationContext) {
            closed = false;
        }

        @Override
        public void openAsync(final OperationContext operationContext, final AsyncCompletionHandler<Void> handler) {
            handler.completed(null);
        }

        @Override
        public void write(final List<ByteBuf> byteBuffers, final OperationContext operationContext) {
            final byte[] header = new byte[MESSAGE_HEADER_LENGTH];
            byteBuffers.get(0).get(byteBuffers.get(0).position(), header);
            lastRequestId = getInt32LittleEndian(header, 4);
            lastOpCode = getInt32LittleEndian(header, 12);
            nextResponse = null;
            nextResponsePosition = 0;
        }

        @Override
        public ByteBuf read(final int numBytes, final OperationContext operationContext) throws IOException {
            return readResponseBytes(numBytes);
        }

        @Override
        public void writeAsync(final List<ByteBuf> byteBuffers, final OperationContext operationContext,
                               final AsyncCompletionHandler<Void> handler) {
            write(byteBuffers, operationContext);
            handler.completed(null);
        }

        @Override
        public void readAsync(final int numBytes, final OperationContext operationContext,
                              final AsyncCompletionHandler<ByteBuf> handler) {
            try {
                handler.completed(read(numBytes, operationContext));
            } catch (IOException e) {
                handler.failed(e);
            }
        }

        @Override
        public ServerAddress getAddress() {
            return serverAddress;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public ByteBuf getBuffer(final int size) {
            return PowerOfTwoBufferPool.DEFAULT.getBuffer(size);
        }

        private ByteBuf readResponseBytes(final int numBytes) throws IOException {
            if (nextResponse == null) {
                nextResponse = createResponse(lastRequestId, lastOpCode, responseRequestId++, responsesCreated++ == 0);
            }
            if (nextResponsePosition + numBytes > nextResponse.length) {
                throw new IOException("No response bytes remaining");
            }
            final ByteBuf buffer = getBuffer(numBytes);
            buffer.put(nextResponse, nextResponsePosition, numBytes);
            buffer.flip();
            nextResponsePosition += numBytes;
            if (nextResponsePosition == nextResponse.length) {
                nextResponse = null;
                nextResponsePosition = 0;
            }
            return buffer;
        }

        private static byte[] createResponse(final int responseTo, final int requestOpCode,
                                             final int responseRequestId, final boolean handshake) {
            final BsonDocument responseDocument = handshake ? helloResponse() : okResponse();
            if (requestOpCode == OP_QUERY) {
                return opReply(responseTo, responseRequestId, responseDocument);
            }
            return opMsg(responseTo, responseRequestId, responseDocument);
        }

        private static BsonDocument helloResponse() {
            return new BsonDocument("ok", new BsonDouble(1.0))
                    .append("isWritablePrimary", BsonBoolean.TRUE)
                    .append("ismaster", BsonBoolean.TRUE)
                    .append("helloOk", BsonBoolean.TRUE)
                    .append("minWireVersion", new BsonInt32(0))
                    .append("maxWireVersion", new BsonInt32(13))
                    .append("maxBsonObjectSize", new BsonInt32(16 * 1024 * 1024))
                    .append("maxMessageSizeBytes", new BsonInt32(48 * 1000 * 1000))
                    .append("maxWriteBatchSize", new BsonInt32(100000))
                    .append("logicalSessionTimeoutMinutes", new BsonInt32(30));
        }

        private static BsonDocument okResponse() {
            return new BsonDocument("ok", new BsonDouble(1.0));
        }

        private static byte[] opReply(final int responseTo, final int requestId, final BsonDocument document) {
            final byte[] documentBytes = toByteArray(document);
            final int bodyLength = 4 + 8 + 4 + 4 + documentBytes.length;
            final ByteBuffer buffer = messageBuffer(responseTo, requestId, OP_REPLY, bodyLength);
            buffer.putInt(0);
            buffer.putLong(0L);
            buffer.putInt(0);
            buffer.putInt(1);
            buffer.put(documentBytes);
            return buffer.array();
        }

        private static byte[] opMsg(final int responseTo, final int requestId, final BsonDocument document) {
            final byte[] documentBytes = toByteArray(document);
            final int bodyLength = 4 + 1 + documentBytes.length;
            final ByteBuffer buffer = messageBuffer(responseTo, requestId, OP_MSG, bodyLength);
            buffer.putInt(0);
            buffer.put((byte) 0);
            buffer.put(documentBytes);
            return buffer.array();
        }

        private static ByteBuffer messageBuffer(final int responseTo, final int requestId, final int opCode,
                                                final int bodyLength) {
            final ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_HEADER_LENGTH + bodyLength)
                    .order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(MESSAGE_HEADER_LENGTH + bodyLength);
            buffer.putInt(requestId);
            buffer.putInt(responseTo);
            buffer.putInt(opCode);
            return buffer;
        }

        private static byte[] toByteArray(final BsonDocument document) {
            final BasicOutputBuffer output = new BasicOutputBuffer();
            try (BsonBinaryWriter writer = new BsonBinaryWriter(output)) {
                new BsonDocumentCodec().encode(writer, document, EncoderContext.builder().build());
            }
            return output.toByteArray();
        }

        private static int getInt32LittleEndian(final byte[] bytes, final int offset) {
            return ByteBuffer.wrap(bytes, offset, Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
    }
}
