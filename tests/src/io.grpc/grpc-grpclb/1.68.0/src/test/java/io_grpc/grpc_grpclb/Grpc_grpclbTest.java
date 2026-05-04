/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_grpclb;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.ChannelLogger;
import io.grpc.ConnectivityState;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.LoadBalancerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.SynchronizationContext;
import io.grpc.grpclb.GrpclbConstants;
import io.grpc.grpclb.GrpclbLoadBalancerProvider;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.lb.v1.ClientStats;
import io.grpc.lb.v1.ClientStatsPerToken;
import io.grpc.lb.v1.FallbackResponse;
import io.grpc.lb.v1.InitialLoadBalanceRequest;
import io.grpc.lb.v1.InitialLoadBalanceResponse;
import io.grpc.lb.v1.LoadBalanceRequest;
import io.grpc.lb.v1.LoadBalanceResponse;
import io.grpc.lb.v1.LoadBalancerGrpc;
import io.grpc.lb.v1.LoadBalancerProto;
import io.grpc.lb.v1.ServerList;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Grpc_grpclbTest {
    @Test
    void generatedMessagesRoundTripThroughProtoParsersAndGrpcMarshallers() throws Exception {
        InitialLoadBalanceRequest initialRequest = InitialLoadBalanceRequest.newBuilder()
                .setName("dns:///payments.example.internal")
                .build();
        LoadBalanceRequest request = LoadBalanceRequest.newBuilder()
                .setInitialRequest(initialRequest)
                .build();

        MethodDescriptor<LoadBalanceRequest, LoadBalanceResponse> method = LoadBalancerGrpc.getBalanceLoadMethod();
        LoadBalanceRequest parsedRequest;
        try (InputStream stream = method.getRequestMarshaller().stream(request)) {
            parsedRequest = method.getRequestMarshaller().parse(stream);
        }

        assertThat(parsedRequest).isEqualTo(request);
        assertThat(parsedRequest.getLoadBalanceRequestTypeCase())
                .isEqualTo(LoadBalanceRequest.LoadBalanceRequestTypeCase.INITIAL_REQUEST);
        assertThat(parsedRequest.getInitialRequest().getName()).isEqualTo("dns:///payments.example.internal");

        io.grpc.lb.v1.Server backend = io.grpc.lb.v1.Server.newBuilder()
                .setIpAddress(ByteString.copyFrom(new byte[] {127, 0, 0, 1}))
                .setPort(8443)
                .setLoadBalanceToken("backend-token")
                .build();
        io.grpc.lb.v1.Server dropEntry = io.grpc.lb.v1.Server.newBuilder()
                .setLoadBalanceToken("rate-limit")
                .setDrop(true)
                .build();
        LoadBalanceResponse response = LoadBalanceResponse.newBuilder()
                .setServerList(ServerList.newBuilder().addServers(backend).addServers(dropEntry))
                .build();

        LoadBalanceResponse parsedResponse;
        try (InputStream stream = method.getResponseMarshaller().stream(response)) {
            parsedResponse = method.getResponseMarshaller().parse(stream);
        }

        assertThat(parsedResponse).isEqualTo(response);
        assertThat(parsedResponse.getLoadBalanceResponseTypeCase())
                .isEqualTo(LoadBalanceResponse.LoadBalanceResponseTypeCase.SERVER_LIST);
        assertThat(parsedResponse.getServerList().getServersList())
                .extracting(io.grpc.lb.v1.Server::getLoadBalanceToken)
                .containsExactly("backend-token", "rate-limit");
        assertThat(parsedResponse.getServerList().getServers(1).getDrop()).isTrue();

        ClientStats stats = ClientStats.newBuilder()
                .setTimestamp(Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123).build())
                .setNumCallsStarted(7)
                .setNumCallsFinished(6)
                .setNumCallsFinishedWithClientFailedToSend(1)
                .setNumCallsFinishedKnownReceived(5)
                .addCallsFinishedWithDrop(ClientStatsPerToken.newBuilder()
                        .setLoadBalanceToken("rate-limit")
                        .setNumCalls(2)
                        .build())
                .build();
        LoadBalanceRequest statsRequest = LoadBalanceRequest.newBuilder().setClientStats(stats).build();
        LoadBalanceRequest parsedStatsRequest = LoadBalanceRequest.parseFrom(statsRequest.toByteArray());

        assertThat(parsedStatsRequest.getLoadBalanceRequestTypeCase())
                .isEqualTo(LoadBalanceRequest.LoadBalanceRequestTypeCase.CLIENT_STATS);
        assertThat(parsedStatsRequest.getClientStats().getCallsFinishedWithDrop(0).getNumCalls()).isEqualTo(2);

        LoadBalanceResponse fallbackResponse = LoadBalanceResponse.newBuilder()
                .setFallbackResponse(FallbackResponse.newBuilder())
                .build();
        ByteArrayInputStream delimitedInput = new ByteArrayInputStream(toDelimitedBytes(fallbackResponse));

        assertThat(LoadBalanceResponse.parseDelimitedFrom(delimitedInput).getLoadBalanceResponseTypeCase())
                .isEqualTo(LoadBalanceResponse.LoadBalanceResponseTypeCase.FALLBACK_RESPONSE);
    }

    @Test
    void serviceAndFileDescriptorsDescribeGrpclbProtocol() {
        MethodDescriptor<LoadBalanceRequest, LoadBalanceResponse> method = LoadBalancerGrpc.getBalanceLoadMethod();

        assertThat(LoadBalancerGrpc.SERVICE_NAME).isEqualTo("grpc.lb.v1.LoadBalancer");
        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.BIDI_STREAMING);
        assertThat(method.getFullMethodName())
                .isEqualTo(MethodDescriptor.generateFullMethodName(LoadBalancerGrpc.SERVICE_NAME, "BalanceLoad"));
        assertThat(LoadBalancerGrpc.getServiceDescriptor().getMethods()).containsExactly(method);
        assertThat(LoadBalancerGrpc.bindService(new LoadBalancerGrpc.AsyncService() {
            @Override
            public StreamObserver<LoadBalanceRequest> balanceLoad(
                    StreamObserver<LoadBalanceResponse> responseObserver) {
                return new NoopRequestObserver();
            }
        }).getMethods()).hasSize(1);

        Descriptors.FileDescriptor descriptor = LoadBalancerProto.getDescriptor();
        assertThat(descriptor.getPackage()).isEqualTo("grpc.lb.v1");
        assertThat(descriptor.findServiceByName("LoadBalancer").findMethodByName("BalanceLoad").isClientStreaming())
                .isTrue();
        assertThat(descriptor.findServiceByName("LoadBalancer").findMethodByName("BalanceLoad").isServerStreaming())
                .isTrue();
        assertThat(LoadBalanceRequest.getDescriptor().getOneofs().get(0).getName())
                .isEqualTo("load_balance_request_type");
        assertThat(LoadBalanceResponse.getDescriptor().getOneofs().get(0).getName())
                .isEqualTo("load_balance_response_type");
        assertThat(io.grpc.lb.v1.Server.getDescriptor().findFieldByName("load_balance_token").getNumber())
                .isEqualTo(io.grpc.lb.v1.Server.LOAD_BALANCE_TOKEN_FIELD_NUMBER);
    }

    @Test
    void loadBalancerProviderParsesPublicServiceConfigsAndExposesConstants() {
        GrpclbLoadBalancerProvider provider = new GrpclbLoadBalancerProvider();

        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.getPriority()).isGreaterThan(0);
        assertThat(provider.getPolicyName()).isEqualTo("grpclb");

        NameResolver.ConfigOrError defaultConfig = provider.parseLoadBalancingPolicyConfig(null);
        assertThat(defaultConfig.getError()).isNull();
        assertThat(defaultConfig.getConfig().toString()).contains("ROUND_ROBIN");

        NameResolver.ConfigOrError roundRobinConfig = provider.parseLoadBalancingPolicyConfig(Map.of(
                "serviceName", "payments",
                "initialFallbackTimeout", "3s",
                "childPolicy", List.of(Map.of("round_robin", Map.of()))));
        assertThat(roundRobinConfig.getError()).isNull();
        assertThat(roundRobinConfig.getConfig().toString())
                .contains("ROUND_ROBIN", "payments", "fallbackTimeoutMs=3000");

        NameResolver.ConfigOrError pickFirstConfig = provider.parseLoadBalancingPolicyConfig(Map.of(
                "childPolicy", List.of(Map.of("pick_first", Map.of()))));
        assertThat(pickFirstConfig.getError()).isNull();
        assertThat(pickFirstConfig.getConfig().toString()).contains("PICK_FIRST");

        NameResolver.ConfigOrError unavailableConfig = provider.parseLoadBalancingPolicyConfig(Map.of(
                "childPolicy", List.of(Map.of("not_installed_policy", Map.of()))));
        assertThat(unavailableConfig.getConfig()).isNull();
        assertThat(unavailableConfig.getError().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(unavailableConfig.getError().getDescription())
                .contains("not_installed_policy", "specified child policies are available");

        assertThat(GrpclbConstants.TOKEN_METADATA_KEY.name()).isEqualTo("lb-token");
        assertThat(GrpclbConstants.ATTR_LB_ADDRS).isNotNull();
        assertThat(GrpclbConstants.ATTR_LB_ADDR_AUTHORITY).isNotNull();
    }

    @Test
    void serviceLoaderRegistersGrpclbLoadBalancerProvider() {
        assertThat(LoadBalancerRegistry.getDefaultRegistry().getProvider("grpclb"))
                .isNotNull()
                .isInstanceOf(GrpclbLoadBalancerProvider.class);
    }

    @Test
    void nameResolverRegistryUsesGrpclbDnsProviderForDnsTargets() throws Exception {
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        NameResolver resolver = null;
        try {
            NameResolverProvider provider = NameResolverRegistry.getDefaultRegistry().getProviderForScheme("dns");
            assertThat(provider).isNotNull();
            assertThat(provider.getDefaultScheme()).isEqualTo("dns");
            assertThat(provider.getProducedSocketAddressTypes()).containsExactly(InetSocketAddress.class);

            NameResolver.Args args = NameResolver.Args.newBuilder()
                    .setDefaultPort(443)
                    .setProxyDetector(serverAddress -> null)
                    .setSynchronizationContext(new SynchronizationContext((thread, throwable) -> {
                        throw new AssertionError("Unexpected resolver synchronization context failure", throwable);
                    }))
                    .setScheduledExecutorService(scheduledExecutorService)
                    .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                        @Override
                        public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> serviceConfig) {
                            return NameResolver.ConfigOrError.fromConfig(serviceConfig);
                        }
                    })
                    .setChannelLogger(new NoopChannelLogger())
                    .build();

            resolver = provider.newNameResolver(URI.create("dns:///orders.example.internal"), args);
            assertThat(resolver).isNotNull();
            assertThat(resolver.getServiceAuthority()).isEqualTo("orders.example.internal");
            assertThat(provider.newNameResolver(URI.create("unix:///tmp/grpc.sock"), args)).isNull();
        } finally {
            if (resolver != null) {
                resolver.shutdown();
            }
            scheduledExecutorService.shutdownNow();
            assertThat(scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void loadBalancerReportsEmptyResolutionAsTransientFailure() throws Exception {
        RecordingLoadBalancerHelper helper = new RecordingLoadBalancerHelper();
        LoadBalancer loadBalancer = null;
        try {
            loadBalancer = new GrpclbLoadBalancerProvider().newLoadBalancer(helper);
            assertThat(loadBalancer.canHandleEmptyAddressListFromNameResolution()).isTrue();

            Status status = loadBalancer.acceptResolvedAddresses(LoadBalancer.ResolvedAddresses.newBuilder()
                    .setAddresses(List.of())
                    .setAttributes(Attributes.EMPTY)
                    .build());

            assertThat(status.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(status.getDescription()).isEqualTo("No backend or balancer addresses found");
            assertThat(helper.latestState.get()).isEqualTo(ConnectivityState.TRANSIENT_FAILURE);
            assertThat(helper.latestPicker.get()).isNotNull();

            LoadBalancer.PickResult pickResult = helper.latestPicker.get().pickSubchannel(new EmptyPickSubchannelArgs());
            assertThat(pickResult.hasResult()).isTrue();
            assertThat(pickResult.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(pickResult.getStatus().getDescription()).isEqualTo("No backend or balancer addresses found");
        } finally {
            if (loadBalancer != null) {
                loadBalancer.shutdown();
            }
            helper.shutdown();
        }
    }

    @Test
    void inProcessStubStreamsInitialResponseServerListAndCompletion() throws Exception {
        String serverName = "grpclb-" + UUID.randomUUID();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> clientError = new AtomicReference<>();
        List<LoadBalanceResponse> responses = new ArrayList<>();

        Server grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(LoadBalancerGrpc.bindService(new TestLoadBalancerService()))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        try {
            StreamObserver<LoadBalanceRequest> requests = LoadBalancerGrpc.newStub(channel)
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .balanceLoad(new StreamObserver<>() {
                        @Override
                        public void onNext(LoadBalanceResponse value) {
                            responses.add(value);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            clientError.set(throwable);
                            completed.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            completed.countDown();
                        }
                    });

            requests.onNext(LoadBalanceRequest.newBuilder()
                    .setInitialRequest(InitialLoadBalanceRequest.newBuilder().setName("integration-service"))
                    .build());
            requests.onNext(LoadBalanceRequest.newBuilder()
                    .setClientStats(ClientStats.newBuilder().setNumCallsStarted(1).setNumCallsFinished(1))
                    .build());
            requests.onCompleted();

            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(clientError.get()).isNull();
            assertThat(responses)
                    .extracting(LoadBalanceResponse::getLoadBalanceResponseTypeCase)
                    .containsExactly(
                            LoadBalanceResponse.LoadBalanceResponseTypeCase.INITIAL_RESPONSE,
                            LoadBalanceResponse.LoadBalanceResponseTypeCase.SERVER_LIST,
                            LoadBalanceResponse.LoadBalanceResponseTypeCase.FALLBACK_RESPONSE);
            assertThat(responses.get(0).getInitialResponse().getClientStatsReportInterval().getSeconds()).isEqualTo(1);
            assertThat(responses.get(1).getServerList().getServers(0).getLoadBalanceToken())
                    .isEqualTo("in-process-token");
        } finally {
            channel.shutdownNow();
            grpcServer.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(grpcServer.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static byte[] toDelimitedBytes(LoadBalanceResponse response) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.writeDelimitedTo(output);
        return output.toByteArray();
    }

    private static final class RecordingLoadBalancerHelper extends LoadBalancer.Helper {
        private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        private final SynchronizationContext synchronizationContext = new SynchronizationContext((thread, throwable) -> {
            throw new AssertionError("Unexpected synchronization context failure", throwable);
        });
        private final ChannelLogger channelLogger = new NoopChannelLogger();
        private final AtomicReference<ConnectivityState> latestState = new AtomicReference<>();
        private final AtomicReference<LoadBalancer.SubchannelPicker> latestPicker = new AtomicReference<>();

        @Override
        public ManagedChannel createOobChannel(EquivalentAddressGroup eag, String authority) {
            throw new AssertionError("Empty resolution should not create an out-of-band channel");
        }

        @Override
        public void updateBalancingState(ConnectivityState newState, LoadBalancer.SubchannelPicker newPicker) {
            latestState.set(newState);
            latestPicker.set(newPicker);
        }

        @Override
        public SynchronizationContext getSynchronizationContext() {
            return synchronizationContext;
        }

        @Override
        public ScheduledExecutorService getScheduledExecutorService() {
            return scheduledExecutorService;
        }

        @Override
        public String getAuthority() {
            return "test-authority.example";
        }

        @Override
        public ChannelLogger getChannelLogger() {
            return channelLogger;
        }

        void shutdown() throws InterruptedException {
            scheduledExecutorService.shutdownNow();
            assertThat(scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class EmptyPickSubchannelArgs extends LoadBalancer.PickSubchannelArgs {
        @Override
        public CallOptions getCallOptions() {
            return CallOptions.DEFAULT;
        }

        @Override
        public Metadata getHeaders() {
            return new Metadata();
        }

        @Override
        public MethodDescriptor<?, ?> getMethodDescriptor() {
            return LoadBalancerGrpc.getBalanceLoadMethod();
        }
    }

    private static final class NoopChannelLogger extends ChannelLogger {
        @Override
        public void log(ChannelLogLevel level, String message) {
            // The test records load-balancer state directly instead of asserting logs.
        }

        @Override
        public void log(ChannelLogLevel level, String messageFormat, Object... args) {
            // The test records load-balancer state directly instead of asserting logs.
        }
    }

    private static final class NoopRequestObserver implements StreamObserver<LoadBalanceRequest> {
        @Override
        public void onNext(LoadBalanceRequest value) {
            // The descriptor-binding test never sends requests to this observer.
        }

        @Override
        public void onError(Throwable throwable) {
            // The descriptor-binding test never sends requests to this observer.
        }

        @Override
        public void onCompleted() {
            // The descriptor-binding test never sends requests to this observer.
        }
    }

    private static final class TestLoadBalancerService implements LoadBalancerGrpc.AsyncService {
        @Override
        public StreamObserver<LoadBalanceRequest> balanceLoad(StreamObserver<LoadBalanceResponse> responseObserver) {
            return new StreamObserver<>() {
                @Override
                public void onNext(LoadBalanceRequest request) {
                    if (request.hasInitialRequest()) {
                        responseObserver.onNext(LoadBalanceResponse.newBuilder()
                                .setInitialResponse(InitialLoadBalanceResponse.newBuilder()
                                        .setClientStatsReportInterval(Duration.newBuilder().setSeconds(1)))
                                .build());
                    } else if (request.hasClientStats()) {
                        responseObserver.onNext(LoadBalanceResponse.newBuilder()
                                .setServerList(ServerList.newBuilder().addServers(io.grpc.lb.v1.Server.newBuilder()
                                        .setIpAddress(ByteString.copyFrom(new byte[] {10, 0, 0, 7}))
                                        .setPort(50051)
                                        .setLoadBalanceToken("in-process-token")))
                                .build());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(LoadBalanceResponse.newBuilder()
                            .setFallbackResponse(FallbackResponse.newBuilder())
                            .build());
                    responseObserver.onCompleted();
                }
            };
        }
    }
}
