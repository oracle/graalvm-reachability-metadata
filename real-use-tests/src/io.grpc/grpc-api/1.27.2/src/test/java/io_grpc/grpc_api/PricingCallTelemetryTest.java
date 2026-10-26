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
import io.grpc.ClientStreamTracer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
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

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's client stream-tracing subsystem for per-request pricing usage telemetry.
public class PricingCallTelemetryTest {
    private static final String SERVICE_NAME = "checkout.MeteredPricing";
    private static final MethodDescriptor<StringValue, Int32Value> QUOTE =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Quote"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void recordsMessageUsageAndOutcomeForSuccessfulAndRejectedQuotes() throws Exception {
        PricingService pricing = new PricingService(Map.of("NOTEBOOK-42", 600, "PENCIL-7", 125));
        Server server = ServerBuilder.forPort(0).addService(pricing.bindService()).build().start();
        ManagedChannel channel = null;
        BlockingQueue<QuoteUsage> telemetry = new LinkedBlockingQueue<>();
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .disableRetry()
                    .build();

            assertEquals(600, quote(channel, "NOTEBOOK-42", telemetry));
            assertEquals(new QuoteUsage("NOTEBOOK-42", 1, 1, Status.Code.OK),
                    telemetry.poll(10, TimeUnit.SECONDS));
            assertEquals(125, quote(channel, "PENCIL-7", telemetry));
            assertEquals(new QuoteUsage("PENCIL-7", 1, 1, Status.Code.OK),
                    telemetry.poll(10, TimeUnit.SECONDS));

            Channel client = channel;
            StatusRuntimeException rejected = assertThrows(StatusRuntimeException.class,
                    () -> quote(client, "UNKNOWN-SKU", telemetry));
            assertEquals(Status.Code.NOT_FOUND, rejected.getStatus().getCode());
            assertEquals("Product is not priced", rejected.getStatus().getDescription());
            // Failed quotes still consume a request, but must not count a response message.
            assertEquals(new QuoteUsage("UNKNOWN-SKU", 1, 0, Status.Code.NOT_FOUND),
                    telemetry.poll(10, TimeUnit.SECONDS));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Pricing server did not terminate");
        }
    }

    private static int quote(Channel channel, String sku, BlockingQueue<QuoteUsage> telemetry) {
        CallOptions options = CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS)
                .withStreamTracerFactory(new UsageRecorder(sku, telemetry));
        return ClientCalls.blockingUnaryCall(channel, QUOTE, options, StringValue.of(sku)).getValue();
    }

    private record QuoteUsage(String sku, int requests, int responses, Status.Code outcome) {
    }

    private static final class UsageRecorder extends ClientStreamTracer.Factory {
        private final String sku;
        private final BlockingQueue<QuoteUsage> telemetry;

        private UsageRecorder(String sku, BlockingQueue<QuoteUsage> telemetry) {
            this.sku = sku;
            this.telemetry = telemetry;
        }

        @Override
        public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
            return new ClientStreamTracer() {
                private final AtomicInteger requests = new AtomicInteger();
                private final AtomicInteger responses = new AtomicInteger();

                @Override
                public void outboundMessage(int sequenceNumber) {
                    requests.incrementAndGet();
                }

                @Override
                public void inboundMessage(int sequenceNumber) {
                    responses.incrementAndGet();
                }

                @Override
                public void streamClosed(Status status) {
                    // Publish one immutable event for the application's usage collector.
                    telemetry.add(new QuoteUsage(sku, requests.get(), responses.get(), status.getCode()));
                }
            };
        }
    }

    private static final class PricingService {
        private final Map<String, Integer> prices;

        private PricingService(Map<String, Integer> prices) {
            this.prices = Map.copyOf(prices);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(QUOTE, ServerCalls.asyncUnaryCall(this::quote))
                    .build();
        }

        private void quote(StringValue request, StreamObserver<Int32Value> response) {
            Integer cents = prices.get(request.getValue());
            if (cents == null) {
                response.onError(Status.NOT_FOUND.withDescription("Product is not priced").asRuntimeException());
                return;
            }
            response.onNext(Int32Value.of(cents));
            response.onCompleted();
        }
    }
}
