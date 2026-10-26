/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's service-configured client retry subsystem for resilient catalog lookups.
public class CatalogRetryPolicyTest {
    private static final String SERVICE_NAME = "catalog.ResilientCatalog";
    private static final MethodDescriptor<StringValue, StringValue> LOOKUP =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Lookup"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void retriesTransientLookupsButBoundsAttemptsAndPreservesPermanentErrors() throws Exception {
        CatalogService catalog = new CatalogService();
        Server server = ServerBuilder.forPort(0).addService(catalog.bindService()).build().start();
        ManagedChannel channel = null;
        try {
            // Only the idempotent lookup method is eligible for application-level retries.
            Map<String, Object> retryPolicy = Map.of(
                    "maxAttempts", 3.0,
                    "initialBackoff", "0.01s",
                    "maxBackoff", "0.1s",
                    "backoffMultiplier", 1.0,
                    "retryableStatusCodes", List.of("UNAVAILABLE"));
            Map<String, Object> methodConfig = Map.of(
                    "name", List.of(Map.of("service", SERVICE_NAME, "method", "Lookup")),
                    "retryPolicy", retryPolicy);
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .defaultServiceConfig(Map.of("methodConfig", List.of(methodConfig)))
                    .disableServiceConfigLookUp()
                    .enableRetry()
                    .build();

            // The catalog partition becomes readable on its third access, without a timed outage.
            assertEquals("Refillable notebook", lookup(channel, "NOTEBOOK-42"));
            assertEquals(3, catalog.attemptsFor("NOTEBOOK-42"));

            Channel client = channel;
            StatusRuntimeException exhausted = assertThrows(StatusRuntimeException.class,
                    () -> lookup(client, "OFFLINE-PARTITION"));
            assertEquals(Status.Code.UNAVAILABLE, exhausted.getStatus().getCode());
            assertEquals("Catalog partition is offline", exhausted.getStatus().getDescription());
            assertEquals(3, catalog.attemptsFor("OFFLINE-PARTITION"));

            StatusRuntimeException missing = assertThrows(StatusRuntimeException.class,
                    () -> lookup(client, "UNKNOWN-SKU"));
            assertEquals(Status.Code.NOT_FOUND, missing.getStatus().getCode());
            assertEquals("Unknown product", missing.getStatus().getDescription());
            assertEquals(1, catalog.attemptsFor("UNKNOWN-SKU"));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Catalog server did not terminate");
        }
    }

    private static String lookup(Channel channel, String sku) {
        return ClientCalls.blockingUnaryCall(channel, LOOKUP,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of(sku)).getValue();
    }

    private static final class CatalogService {
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(LOOKUP, ServerCalls.asyncUnaryCall(this::lookup))
                    .build();
        }

        private int attemptsFor(String sku) {
            return attempts.get(sku).get();
        }

        private void lookup(StringValue request, StreamObserver<StringValue> response) {
            String sku = request.getValue();
            int attempt = attempts.computeIfAbsent(sku, ignored -> new AtomicInteger()).incrementAndGet();
            if ("OFFLINE-PARTITION".equals(sku)) {
                response.onError(Status.UNAVAILABLE.withDescription("Catalog partition is offline")
                        .asRuntimeException());
            } else if (!"NOTEBOOK-42".equals(sku)) {
                response.onError(Status.NOT_FOUND.withDescription("Unknown product").asRuntimeException());
            } else if (attempt < 3) {
                response.onError(Status.UNAVAILABLE.withDescription("Catalog partition is reconnecting")
                        .asRuntimeException());
            } else {
                response.onNext(StringValue.of("Refillable notebook"));
                response.onCompleted();
            }
        }
    }
}
