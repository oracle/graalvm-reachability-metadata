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
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's request-scoped context propagation subsystem for asynchronous tenant pricing.
public class AsyncTenantContextTest {
    private static final Metadata.Key<String> TENANT_HEADER =
            Metadata.Key.of("tenant-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<String> TENANT = Context.keyWithDefault("pricing-tenant", "public");
    private static final String SERVICE_NAME = "catalog.PricingService";
    private static final MethodDescriptor<StringValue, Int32Value> QUOTE =
            MethodDescriptor.<StringValue, Int32Value>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Quote"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(Int32Value.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void keepsTenantPricingIsolatedAcrossAsynchronousRequests() throws Exception {
        ExecutorService pricingWorkers = Executors.newFixedThreadPool(2);
        Server server = null;
        ManagedChannel channel = null;
        try {
            PricingService pricing = new PricingService(pricingWorkers, Map.of(
                    "retail", Map.of("NOTEBOOK-42", 600),
                    "wholesale", Map.of("NOTEBOOK-42", 425),
                    "public", Map.of("NOTEBOOK-42", 750)));
            server = ServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(pricing.bindService(), new TenantContextInterceptor()))
                    .build().start();
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();

            Channel retail = forTenant(channel, "retail");
            Channel wholesale = forTenant(channel, "wholesale");
            CompletableFuture<Integer> retailQuote = quote(retail, "NOTEBOOK-42");
            CompletableFuture<Integer> wholesaleQuote = quote(wholesale, "NOTEBOOK-42");

            assertEquals(600, retailQuote.get(10, TimeUnit.SECONDS).intValue());
            assertEquals(425, wholesaleQuote.get(10, TimeUnit.SECONDS).intValue());
            // A caller without a tenant must not inherit a previous request's pricing context.
            assertEquals(750, quote(channel, "NOTEBOOK-42").get(10, TimeUnit.SECONDS).intValue());
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            if (server != null) {
                server.shutdownNow();
            }
            pricingWorkers.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            if (server != null) {
                assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Pricing server did not terminate");
            }
            assertTrue(pricingWorkers.awaitTermination(10, TimeUnit.SECONDS), "Pricing workers did not terminate");
        }
    }

    private static Channel forTenant(Channel channel, String tenant) {
        Metadata headers = new Metadata();
        headers.put(TENANT_HEADER, tenant);
        return ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static CompletableFuture<Integer> quote(Channel channel, String product) {
        CompletableFuture<Integer> result = new CompletableFuture<>();
        ClientCalls.asyncUnaryCall(
                channel.newCall(QUOTE, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS)),
                StringValue.of(product), new StreamObserver<>() {
                    private int price;

                    @Override
                    public void onNext(Int32Value response) {
                        price = response.getValue();
                    }

                    @Override
                    public void onError(Throwable failure) {
                        result.completeExceptionally(failure);
                    }

                    @Override
                    public void onCompleted() {
                        result.complete(price);
                    }
                });
        return result;
    }

    private static final class TenantContextInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String tenant = headers.get(TENANT_HEADER);
            Context requestContext = Context.current().withValue(TENANT, tenant == null ? "public" : tenant);
            return Contexts.interceptCall(requestContext, call, headers, next);
        }
    }

    private static final class PricingService {
        private final ExecutorService workers;
        private final Map<String, Map<String, Integer>> pricesByTenant;

        private PricingService(ExecutorService workers, Map<String, Map<String, Integer>> pricesByTenant) {
            this.workers = workers;
            this.pricesByTenant = Map.copyOf(pricesByTenant);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(QUOTE, ServerCalls.asyncUnaryCall(this::quote))
                    .build();
        }

        private void quote(StringValue product, StreamObserver<Int32Value> response) {
            // Capture the RPC context before handing work to the application's pricing pool.
            workers.execute(Context.current().wrap(() -> {
                int price = pricesByTenant.get(TENANT.get()).get(product.getValue());
                response.onNext(Int32Value.of(price));
                response.onCompleted();
            }));
        }
    }
}
