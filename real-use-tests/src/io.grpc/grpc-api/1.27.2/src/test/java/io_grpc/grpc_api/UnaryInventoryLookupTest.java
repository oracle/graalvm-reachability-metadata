/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's unary RPC transport and service dispatch subsystem for an inventory lookup.
public class UnaryInventoryLookupTest {
    private static final String SERVICE_NAME = "inventory.StockService";
    private static final MethodDescriptor<StringValue, Int32Value> LOOKUP =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Lookup"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void readsStockForMultipleProductsOverOneChannel() throws Exception {
        InventoryService inventory = new InventoryService(Map.of("NOTEBOOK-42", 18, "PENCIL-7", 240));
        Server server = ServerBuilder.forPort(0).addService(inventory).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();

            Int32Value notebooks = ClientCalls.blockingUnaryCall(channel, LOOKUP,
                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of("NOTEBOOK-42"));
            Int32Value pencils = ClientCalls.blockingUnaryCall(channel, LOOKUP,
                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of("PENCIL-7"));

            assertEquals(18, notebooks.getValue());
            assertEquals(240, pencils.getValue());
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Inventory server did not terminate");
        }
    }

    private static final class InventoryService implements BindableService {
        private final Map<String, Integer> stock;

        private InventoryService(Map<String, Integer> stock) {
            this.stock = Map.copyOf(stock);
        }

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(LOOKUP, ServerCalls.asyncUnaryCall(this::lookup))
                    .build();
        }

        private void lookup(StringValue request, StreamObserver<Int32Value> response) {
            response.onNext(Int32Value.of(stock.getOrDefault(request.getValue(), 0)));
            response.onCompleted();
        }
    }
}
