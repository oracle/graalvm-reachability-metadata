/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Deadline;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's client-side round-robin load-balancing subsystem for replicated warehouse lookups.
public class ReplicaLoadBalancingTest {
    private static final String SERVICE_NAME = "warehouse.ReplicatedStock";
    private static final MethodDescriptor<StringValue, StringValue> LOOKUP =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Lookup"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void distributesStockLookupsAcrossReadyReplicas() throws Exception {
        List<Server> servers = new ArrayList<>();
        ManagedChannel channel = null;
        try {
            Map<String, Integer> stock = Map.of("NOTEBOOK-42", 18);
            servers.add(ServerBuilder.forPort(0)
                    .addService(new StockReplica("west-a", stock).bindService()).build().start());
            servers.add(ServerBuilder.forPort(0)
                    .addService(new StockReplica("west-b", stock).bindService()).build().start());
            List<EquivalentAddressGroup> endpoints = new ArrayList<>();
            for (Server server : servers) {
                // Separate address groups identify independent replicas, not alternate addresses of one host.
                endpoints.add(new EquivalentAddressGroup(new InetSocketAddress("127.0.0.1", server.getPort())));
            }
            channel = ManagedChannelBuilder.forTarget("replicas:///stock")
                    .nameResolverFactory(new ReplicaDirectory(endpoints))
                    .defaultLoadBalancingPolicy("round_robin")
                    .usePlaintext()
                    .build();

            Set<String> expected = Set.of("west-a:NOTEBOOK-42=18", "west-b:NOTEBOOK-42=18");
            Set<String> observed = new HashSet<>();
            Deadline readiness = Deadline.after(10, TimeUnit.SECONDS);
            // Probe real responses until both connections are ready; startup order is not significant.
            while (observed.size() < expected.size() && !readiness.isExpired()) {
                String reply = lookup(channel);
                assertTrue(expected.contains(reply), "Replica returned incorrect stock: " + reply);
                observed.add(reply);
            }
            assertEquals(expected, observed, "Both replicas must serve requests through the same channel");

            List<String> batch = new ArrayList<>();
            for (int request = 0; request < 6; request++) {
                batch.add(lookup(channel));
            }
            // With both replicas ready, a six-request batch must be shared equally, regardless of first pick.
            assertEquals(3, Collections.frequency(batch, "west-a:NOTEBOOK-42=18"));
            assertEquals(3, Collections.frequency(batch, "west-b:NOTEBOOK-42=18"));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            for (Server server : servers) {
                server.shutdownNow();
            }
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            for (Server server : servers) {
                assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Stock replica did not terminate");
            }
        }
    }

    private static String lookup(ManagedChannel channel) {
        return ClientCalls.blockingUnaryCall(channel, LOOKUP,
                CallOptions.DEFAULT.withWaitForReady().withDeadlineAfter(10, TimeUnit.SECONDS),
                StringValue.of("NOTEBOOK-42")).getValue();
    }

    private static final class ReplicaDirectory extends NameResolver.Factory {
        private final List<EquivalentAddressGroup> endpoints;

        private ReplicaDirectory(List<EquivalentAddressGroup> endpoints) {
            this.endpoints = List.copyOf(endpoints);
        }

        @Override
        public NameResolver newNameResolver(URI target, NameResolver.Args args) {
            if (!getDefaultScheme().equals(target.getScheme())) {
                return null;
            }
            return new NameResolver() {
                @Override
                public String getServiceAuthority() {
                    return "stock.warehouse.internal";
                }

                @Override
                public void start(Listener2 listener) {
                    listener.onResult(ResolutionResult.newBuilder().setAddresses(endpoints).build());
                }

                @Override
                public void shutdown() {
                    // The deployment's fixed replica list owns no background resources.
                }
            };
        }

        @Override
        public String getDefaultScheme() {
            return "replicas";
        }
    }

    private static final class StockReplica {
        private final String replica;
        private final Map<String, Integer> stock;

        private StockReplica(String replica, Map<String, Integer> stock) {
            this.replica = replica;
            this.stock = Map.copyOf(stock);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(LOOKUP, ServerCalls.asyncUnaryCall(this::lookup))
                    .build();
        }

        private void lookup(StringValue request, StreamObserver<StringValue> response) {
            String sku = request.getValue();
            response.onNext(StringValue.of(replica + ":" + sku + "=" + stock.getOrDefault(sku, 0)));
            response.onCompleted();
        }
    }
}
