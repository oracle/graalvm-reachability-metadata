/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Duration;
import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver.ConfigOrError;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.lookup.v1.GrpcKeyBuilder;
import io.grpc.lookup.v1.HttpKeyBuilder;
import io.grpc.lookup.v1.NameMatcher;
import io.grpc.lookup.v1.RouteLookupClusterSpecifier;
import io.grpc.lookup.v1.RouteLookupConfig;
import io.grpc.lookup.v1.RouteLookupRequest;
import io.grpc.lookup.v1.RouteLookupRequest.Reason;
import io.grpc.lookup.v1.RouteLookupResponse;
import io.grpc.lookup.v1.RouteLookupServiceGrpc;
import io.grpc.lookup.v1.RouteLookupServiceGrpc.RouteLookupServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Grpc_rlsTest {
    private static final String LOOKUP_SERVICE_NAME = "dns:///rls.example.test";

    @Test
    void routeLookupMessagesRoundTripThroughProtoEncoding() throws Exception {
        NameMatcher headerMatcher = NameMatcher.newBuilder()
                .setKey("tenant")
                .addNames("x-tenant")
                .addNames("x-fallback-tenant")
                .build();
        GrpcKeyBuilder grpcKeyBuilder = GrpcKeyBuilder.newBuilder()
                .addNames(GrpcKeyBuilder.Name.newBuilder()
                        .setService("example.v1.Inventory")
                        .setMethod("GetItem"))
                .setExtraKeys(GrpcKeyBuilder.ExtraKeys.newBuilder()
                        .setHost("host-key")
                        .setService("service-key")
                        .setMethod("method-key"))
                .addHeaders(headerMatcher)
                .putConstantKeys("environment", "test")
                .build();
        HttpKeyBuilder httpKeyBuilder = HttpKeyBuilder.newBuilder()
                .addHostPatterns("*.example.test")
                .addPathPatterns("/v1/items/*")
                .addQueryParameters(NameMatcher.newBuilder()
                        .setKey("query-tenant")
                        .addNames("tenant"))
                .addHeaders(NameMatcher.newBuilder()
                        .setKey("authorization")
                        .addNames("authorization")
                        .setRequiredMatch(true))
                .putConstantKeys("protocol", "https")
                .build();
        RouteLookupConfig config = RouteLookupConfig.newBuilder()
                .addGrpcKeybuilders(grpcKeyBuilder)
                .addHttpKeybuilders(httpKeyBuilder)
                .setLookupService(LOOKUP_SERVICE_NAME)
                .setLookupServiceTimeout(Duration.newBuilder().setSeconds(2))
                .setMaxAge(Duration.newBuilder().setSeconds(30))
                .setStaleAge(Duration.newBuilder().setSeconds(5))
                .setCacheSizeBytes(4096)
                .addValidTargets("us-east.example.test")
                .addValidTargets("us-west.example.test")
                .setDefaultTarget("default.example.test")
                .build();
        RouteLookupClusterSpecifier clusterSpecifier = RouteLookupClusterSpecifier.newBuilder()
                .setRouteLookupConfig(config)
                .build();

        RouteLookupClusterSpecifier parsedClusterSpecifier = RouteLookupClusterSpecifier.parseFrom(
                clusterSpecifier.toByteArray());
        RouteLookupConfig parsedConfig = parsedClusterSpecifier.getRouteLookupConfig();

        assertThat(parsedConfig).isEqualTo(config);
        assertThat(parsedConfig.getGrpcKeybuilders(0).getNames(0).getService()).isEqualTo("example.v1.Inventory");
        assertThat(parsedConfig.getGrpcKeybuilders(0).getHeaders(0).getNamesList())
                .containsExactly("x-tenant", "x-fallback-tenant");
        assertThat(parsedConfig.getHttpKeybuilders(0).getQueryParameters(0).getKey()).isEqualTo("query-tenant");
        assertThat(parsedConfig.getValidTargetsList()).containsExactly("us-east.example.test", "us-west.example.test");
    }

    @Test
    void routeLookupRequestsAndResponsesPreserveMapsEnumsAndTargets() throws Exception {
        RouteLookupRequest request = RouteLookupRequest.newBuilder()
                .setTargetType("grpc")
                .setReason(Reason.REASON_STALE)
                .setStaleHeaderData("cached-header-data")
                .putKeyMap("tenant", "alpha")
                .putKeyMap("method", "GetItem")
                .build();
        RouteLookupResponse response = RouteLookupResponse.newBuilder()
                .addTargets("primary.example.test")
                .addTargets("secondary.example.test")
                .setHeaderData("fresh-header-data")
                .build();

        RouteLookupRequest parsedRequest = RouteLookupRequest.parseFrom(request.toByteArray());
        RouteLookupResponse parsedResponse = RouteLookupResponse.parseFrom(response.toByteArray());

        assertThat(parsedRequest.getTargetType()).isEqualTo("grpc");
        assertThat(parsedRequest.getReason()).isEqualTo(Reason.REASON_STALE);
        assertThat(parsedRequest.getStaleHeaderData()).isEqualTo("cached-header-data");
        assertThat(parsedRequest.getKeyMapMap()).containsEntry("tenant", "alpha").containsEntry("method", "GetItem");
        assertThat(parsedResponse.getTargetsList()).containsExactly("primary.example.test", "secondary.example.test");
        assertThat(parsedResponse.getHeaderData()).isEqualTo("fresh-header-data");
    }

    @Test
    void routeLookupServiceHandlesBlockingAndAsyncClients() throws Exception {
        RecordingRouteLookupService service = new RecordingRouteLookupService();
        String serverName = InProcessServerBuilder.generateName() + UUID.randomUUID();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(RouteLookupServiceGrpc.bindService(service))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        try {
            RouteLookupRequest blockingRequest = routeLookupRequest("alpha", Reason.REASON_MISS);
            RouteLookupResponse blockingResponse = RouteLookupServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .routeLookup(blockingRequest);
            assertThat(blockingResponse.getTargetsList()).containsExactly("alpha.target.example.test");
            assertThat(blockingResponse.getHeaderData()).isEqualTo("header-for-alpha");
            assertThat(service.lastRequest().getReason()).isEqualTo(Reason.REASON_MISS);

            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RouteLookupResponse> asyncResponse = new AtomicReference<>();
            AtomicReference<Throwable> asyncError = new AtomicReference<>();
            RouteLookupServiceGrpc.newStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .routeLookup(routeLookupRequest("beta", Reason.REASON_STALE),
                            new StreamObserver<RouteLookupResponse>() {
                                @Override
                                public void onNext(RouteLookupResponse value) {
                                    asyncResponse.set(value);
                                }

                                @Override
                                public void onError(Throwable throwable) {
                                    asyncError.set(throwable);
                                    completed.countDown();
                                }

                                @Override
                                public void onCompleted() {
                                    completed.countDown();
                                }
                            });

            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(asyncError.get()).isNull();
            assertThat(asyncResponse.get()).isNotNull();
            assertThat(asyncResponse.get().getTargetsList()).containsExactly("beta.target.example.test");
            assertThat(asyncResponse.get().getHeaderData()).isEqualTo("header-for-beta");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void routeLookupServiceImplBaseSupportsFutureClient() throws Exception {
        BindableRouteLookupService service = new BindableRouteLookupService();
        String serverName = InProcessServerBuilder.generateName() + UUID.randomUUID();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        try {
            ListenableFuture<RouteLookupResponse> future = RouteLookupServiceGrpc.newFutureStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .routeLookup(routeLookupRequest("gamma", Reason.REASON_MISS));

            RouteLookupResponse response = future.get(5, TimeUnit.SECONDS);

            assertThat(response.getTargetsList()).containsExactly("gamma.future.example.test");
            assertThat(response.getHeaderData()).isEqualTo("future-header-for-gamma");
            assertThat(service.lastRequest().getKeyMapMap()).containsEntry("tenant", "gamma");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void routeLookupServiceBlockingV2ClientPropagatesCheckedStatusFailures() throws Exception {
        FailingRouteLookupService service = new FailingRouteLookupService();
        String serverName = InProcessServerBuilder.generateName() + UUID.randomUUID();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        try {
            StatusException exception = assertThrows(StatusException.class, () -> RouteLookupServiceGrpc
                    .newBlockingV2Stub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .routeLookup(routeLookupRequest("delta", Reason.REASON_MISS)));

            Status status = Status.fromThrowable(exception);
            assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
            assertThat(status.getDescription()).isEqualTo("tenant delta is not routable");
            assertThat(service.lastRequest().getKeyMapMap()).containsEntry("tenant", "delta");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void loadBalancerRegistryProvidesRlsPolicyAndParsesConfigs() {
        LoadBalancerProvider provider = LoadBalancerRegistry.getDefaultRegistry().getProvider("rls_experimental");

        assertThat(provider).isNotNull();
        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.getPolicyName()).isEqualTo("rls_experimental");
        assertThat(provider.getPriority()).isPositive();

        ConfigOrError parsed = provider.parseLoadBalancingPolicyConfig(validLoadBalancingConfig());
        assertThat(parsed.getError()).isNull();
        assertThat(parsed.getConfig()).isNotNull();
        assertThat(parsed.getConfig().toString())
                .contains(LOOKUP_SERVICE_NAME)
                .contains("pick_first")
                .contains("target");

        ConfigOrError invalid = provider.parseLoadBalancingPolicyConfig(Map.of(
                "routeLookupConfig", Map.of("lookupService", LOOKUP_SERVICE_NAME),
                "childPolicyConfigTargetFieldName", "target",
                "childPolicy", List.of(Map.of("pick_first", Map.of()))));
        assertThat(invalid.getConfig()).isNull();
        assertThat(invalid.getError()).isNotNull();
        assertThat(invalid.getError().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(invalid.getError().getDescription()).contains("grpcKeybuilders");
    }

    private static RouteLookupRequest routeLookupRequest(String tenant, Reason reason) {
        return RouteLookupRequest.newBuilder()
                .setTargetType("grpc")
                .setReason(reason)
                .putKeyMap("tenant", tenant)
                .build();
    }

    private static Map<String, ?> validLoadBalancingConfig() {
        Map<String, ?> grpcKeyBuilder = Map.of(
                "names", List.of(Map.of(
                        "service", "example.v1.Inventory",
                        "method", "GetItem")),
                "headers", List.of(Map.of(
                        "key", "tenant",
                        "names", List.of("x-tenant"))),
                "extraKeys", Map.of(
                        "host", "host-key",
                        "service", "service-key",
                        "method", "method-key"),
                "constantKeys", Map.of("environment", "test"));
        Map<String, ?> routeLookupConfig = Map.of(
                "grpcKeybuilders", List.of(grpcKeyBuilder),
                "lookupService", LOOKUP_SERVICE_NAME,
                "lookupServiceTimeout", "1s",
                "maxAge", "10s",
                "staleAge", "2s",
                "cacheSizeBytes", 4096.0,
                "defaultTarget", "default.example.test");
        return Map.of(
                "routeLookupConfig", routeLookupConfig,
                "routeLookupChannelServiceConfig", Map.of(
                        "loadBalancingConfig", List.of(Map.of("pick_first", Map.of()))),
                "childPolicyConfigTargetFieldName", "target",
                "childPolicy", List.of(Map.of("pick_first", Map.of())));
    }

    private static final class BindableRouteLookupService extends RouteLookupServiceImplBase {
        private final AtomicReference<RouteLookupRequest> lastRequest = new AtomicReference<>();

        @Override
        public void routeLookup(RouteLookupRequest request, StreamObserver<RouteLookupResponse> responseObserver) {
            lastRequest.set(request);
            String tenant = request.getKeyMapOrDefault("tenant", "unknown");
            responseObserver.onNext(RouteLookupResponse.newBuilder()
                    .addTargets(tenant + ".future.example.test")
                    .setHeaderData("future-header-for-" + tenant)
                    .build());
            responseObserver.onCompleted();
        }

        RouteLookupRequest lastRequest() {
            return lastRequest.get();
        }
    }

    private static final class FailingRouteLookupService extends RouteLookupServiceImplBase {
        private final AtomicReference<RouteLookupRequest> lastRequest = new AtomicReference<>();

        @Override
        public void routeLookup(RouteLookupRequest request, StreamObserver<RouteLookupResponse> responseObserver) {
            lastRequest.set(request);
            String tenant = request.getKeyMapOrDefault("tenant", "unknown");
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("tenant " + tenant + " is not routable")
                    .asException());
        }

        RouteLookupRequest lastRequest() {
            return lastRequest.get();
        }
    }

    private static final class RecordingRouteLookupService implements RouteLookupServiceGrpc.AsyncService {
        private final AtomicReference<RouteLookupRequest> lastRequest = new AtomicReference<>();

        @Override
        public void routeLookup(RouteLookupRequest request, StreamObserver<RouteLookupResponse> responseObserver) {
            lastRequest.set(request);
            String tenant = request.getKeyMapOrDefault("tenant", "unknown");
            responseObserver.onNext(RouteLookupResponse.newBuilder()
                    .addTargets(tenant + ".target.example.test")
                    .setHeaderData("header-for-" + tenant)
                    .build());
            responseObserver.onCompleted();
        }

        RouteLookupRequest lastRequest() {
            return lastRequest.get();
        }
    }
}
