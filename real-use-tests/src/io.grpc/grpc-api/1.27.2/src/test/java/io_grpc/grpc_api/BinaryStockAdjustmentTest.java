/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's custom message-marshalling subsystem for binary warehouse stock adjustments.
public class BinaryStockAdjustmentTest {
    private static final String SERVICE_NAME = "warehouse.BinaryStockService";
    private static final MethodDescriptor<StockEntry, StockEntry> ADJUST =
            MethodDescriptor.<StockEntry, StockEntry>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Adjust"))
                    .setRequestMarshaller(new StockEntryMarshaller())
                    .setResponseMarshaller(new StockEntryMarshaller())
                    .build();

    @Test
    @Timeout(60)
    public void exchangesTypedStockAdjustmentsWithoutProtobuf() throws Exception {
        StockService inventory = new StockService(Map.of("CAFE-\u00e9", 20, "PENCIL-7", 100));
        Server server = ServerBuilder.forPort(0).addService(inventory.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();

            // The scanner protocol carries a UTF product code followed by a signed quantity.
            assertEquals(new StockEntry("CAFE-\u00e9", 17), adjust(channel, new StockEntry("CAFE-\u00e9", -3)));
            assertEquals(new StockEntry("CAFE-\u00e9", 25), adjust(channel, new StockEntry("CAFE-\u00e9", 8)));
            assertEquals(new StockEntry("PENCIL-7", 88), adjust(channel, new StockEntry("PENCIL-7", -12)));
            // A zero adjustment queries the current level without changing it.
            assertEquals(new StockEntry("CAFE-\u00e9", 25), adjust(channel, new StockEntry("CAFE-\u00e9", 0)));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Stock server did not terminate");
        }
    }

    private static StockEntry adjust(ManagedChannel channel, StockEntry adjustment) {
        return ClientCalls.blockingUnaryCall(channel, ADJUST,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), adjustment);
    }

    private record StockEntry(String sku, int quantity) {
    }

    private static final class StockEntryMarshaller implements MethodDescriptor.Marshaller<StockEntry> {
        @Override
        public InputStream stream(StockEntry entry) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(entry.sku());
                output.writeInt(entry.quantity());
            } catch (IOException failure) {
                throw Status.INTERNAL.withDescription("Cannot encode stock entry")
                        .withCause(failure).asRuntimeException();
            }
            return new ByteArrayInputStream(bytes.toByteArray());
        }

        @Override
        public StockEntry parse(InputStream stream) {
            DataInputStream input = new DataInputStream(stream);
            try {
                return new StockEntry(input.readUTF(), input.readInt());
            } catch (IOException failure) {
                throw Status.INVALID_ARGUMENT.withDescription("Invalid binary stock entry")
                        .withCause(failure).asRuntimeException();
            }
        }
    }

    private static final class StockService {
        private final Map<String, Integer> stock;

        private StockService(Map<String, Integer> initialStock) {
            stock = new ConcurrentHashMap<>(initialStock);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(ADJUST, ServerCalls.asyncUnaryCall(this::adjust))
                    .build();
        }

        private void adjust(StockEntry adjustment, StreamObserver<StockEntry> response) {
            int available = stock.merge(adjustment.sku(), adjustment.quantity(), Integer::sum);
            response.onNext(new StockEntry(adjustment.sku(), available));
            response.onCompleted();
        }
    }
}
