/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's custom name-resolution subsystem for warehouse service discovery.
public class WarehouseServiceDiscoveryTest {
    private static final String SERVICE_NAME = "warehouse.AvailabilityService";
    private static final MethodDescriptor<StringValue, Int32Value> AVAILABLE =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Available"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void resolvesALogicalWarehouseNameBeforeLookingUpStock() throws Exception {
        AvailabilityService warehouse = new AvailabilityService(Map.of("NOTEBOOK-42", 36, "PENCIL-7", 120));
        Server server = ServerBuilder.forPort(0).addService(warehouse.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            // Deployment configuration maps logical warehouse names to their advertised endpoints.
            WarehouseDirectory directory = new WarehouseDirectory(Map.of(
                    "west", new InetSocketAddress("127.0.0.1", server.getPort())));
            channel = ManagedChannelBuilder.forTarget("warehouse:///west")
                    .nameResolverFactory(directory)
                    .usePlaintext()
                    .build();

            assertEquals("west.warehouse.internal", channel.authority());
            assertEquals(36, available(channel, "NOTEBOOK-42"));
            assertEquals(120, available(channel, "PENCIL-7"));
            assertEquals(0, available(channel, "UNKNOWN-SKU"));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Warehouse server did not terminate");
        }
    }

    private static int available(ManagedChannel channel, String sku) {
        return ClientCalls.blockingUnaryCall(channel, AVAILABLE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of(sku)).getValue();
    }

    private static final class WarehouseDirectory extends NameResolver.Factory {
        private final Map<String, InetSocketAddress> endpoints;

        private WarehouseDirectory(Map<String, InetSocketAddress> endpoints) {
            this.endpoints = Map.copyOf(endpoints);
        }

        @Override
        public NameResolver newNameResolver(URI target, NameResolver.Args args) {
            if (!getDefaultScheme().equals(target.getScheme())) {
                return null;
            }
            String warehouse = target.getPath().substring(1);
            return new WarehouseResolver(warehouse, endpoints.get(warehouse));
        }

        @Override
        public String getDefaultScheme() {
            return "warehouse";
        }
    }

    private static final class WarehouseResolver extends NameResolver {
        private final String warehouse;
        private final InetSocketAddress endpoint;

        private WarehouseResolver(String warehouse, InetSocketAddress endpoint) {
            this.warehouse = warehouse;
            this.endpoint = endpoint;
        }

        @Override
        public String getServiceAuthority() {
            return warehouse + ".warehouse.internal";
        }

        @Override
        public void start(NameResolver.Listener2 listener) {
            if (endpoint == null) {
                listener.onError(Status.UNAVAILABLE.withDescription("Warehouse is not configured: " + warehouse));
                return;
            }
            listener.onResult(NameResolver.ResolutionResult.newBuilder()
                    .setAddresses(List.of(new EquivalentAddressGroup(endpoint)))
                    .build());
        }

        @Override
        public void shutdown() {
            // This immutable deployment directory owns no sockets, watchers, or executors.
        }
    }

    private static final class AvailabilityService {
        private final Map<String, Integer> stock;

        private AvailabilityService(Map<String, Integer> stock) {
            this.stock = Map.copyOf(stock);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(AVAILABLE, ServerCalls.asyncUnaryCall(this::available))
                    .build();
        }

        private void available(StringValue sku, StreamObserver<Int32Value> response) {
            response.onNext(Int32Value.of(stock.getOrDefault(sku.getValue(), 0)));
            response.onCompleted();
        }
    }
}
