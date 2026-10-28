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
import io.grpc.util.MutableHandlerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's dynamic service-registration subsystem for hot-swappable shipping-rate plugins.
public class DynamicShippingPluginTest {
    private static final String SERVICE_NAME = "checkout.ShippingRates";
    private static final MethodDescriptor<StringValue, Int32Value> QUOTE =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Quote"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void installsReplacesAndRemovesShippingRatesWithoutRestartingTheServer() throws Exception {
        MutableHandlerRegistry plugins = new MutableHandlerRegistry();
        Server server = ServerBuilder.forPort(0).fallbackHandlerRegistry(plugins).build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();
            assertPluginUnavailable(channel);

            ShippingRates regularRates = new ShippingRates(Map.of("standard", 600, "express", 1200));
            plugins.addService(regularRates.bindService());
            assertEquals(600, quote(channel, "standard"));
            assertEquals(1200, quote(channel, "express"));

            // Installing a seasonal plugin under the same service name replaces future RPC routing.
            ShippingRates seasonalRates = new ShippingRates(Map.of("standard", 400, "express", 900));
            ServerServiceDefinition seasonalPlugin = seasonalRates.bindService();
            plugins.addService(seasonalPlugin);
            assertEquals(400, quote(channel, "standard"));
            assertEquals(900, quote(channel, "express"));

            // Disabling the integration removes its endpoint, not the server or the client's channel.
            assertTrue(plugins.removeService(seasonalPlugin));
            assertPluginUnavailable(channel);

            plugins.addService(regularRates.bindService());
            assertEquals(600, quote(channel, "standard"));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Plugin server did not terminate");
        }
    }

    private static int quote(Channel channel, String deliveryMethod) {
        return ClientCalls.blockingUnaryCall(channel, QUOTE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS),
                StringValue.of(deliveryMethod)).getValue();
    }

    private static void assertPluginUnavailable(Channel channel) {
        StatusRuntimeException failure = assertThrows(StatusRuntimeException.class,
                () -> quote(channel, "standard"));
        assertEquals(Status.Code.UNIMPLEMENTED, failure.getStatus().getCode());
    }

    private static final class ShippingRates {
        private final Map<String, Integer> centsByDeliveryMethod;

        private ShippingRates(Map<String, Integer> centsByDeliveryMethod) {
            this.centsByDeliveryMethod = Map.copyOf(centsByDeliveryMethod);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(QUOTE, ServerCalls.asyncUnaryCall(this::quote))
                    .build();
        }

        private void quote(StringValue request, StreamObserver<Int32Value> response) {
            Integer cents = centsByDeliveryMethod.get(request.getValue());
            if (cents == null) {
                response.onError(Status.INVALID_ARGUMENT.withDescription("Unknown delivery method")
                        .asRuntimeException());
                return;
            }
            response.onNext(Int32Value.of(cents));
            response.onCompleted();
        }
    }
}
