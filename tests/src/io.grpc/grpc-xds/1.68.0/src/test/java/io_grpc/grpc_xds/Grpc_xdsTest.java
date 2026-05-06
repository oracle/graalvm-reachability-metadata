/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_xds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.google.protobuf.Duration;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ChannelCredentials;
import io.grpc.ClientCall;
import io.grpc.ClientStreamTracer;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import io.grpc.NameResolverRegistry;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.xds.CdsLoadBalancerProvider;
import io.grpc.xds.ClusterImplLoadBalancerProvider;
import io.grpc.xds.ClusterResolverLoadBalancerProvider;
import io.grpc.xds.CsdsService;
import io.grpc.xds.LeastRequestLoadBalancerProvider;
import io.grpc.xds.PriorityLoadBalancerProvider;
import io.grpc.xds.RingHashLoadBalancerProvider;
import io.grpc.xds.WeightedRoundRobinLoadBalancerProvider;
import io.grpc.xds.WeightedTargetLoadBalancerProvider;
import io.grpc.xds.WrrLocalityLoadBalancerProvider;
import io.grpc.xds.XdsChannelCredentials;
import io.grpc.xds.XdsNameResolverProvider;
import io.grpc.xds.XdsServerBuilder;
import io.grpc.xds.XdsServerCredentials;
import io.grpc.xds.client.Bootstrapper;
import io.grpc.xds.client.BootstrapperImpl;
import io.grpc.xds.client.EnvoyProtoData;
import io.grpc.xds.client.XdsInitializationException;
import io.grpc.xds.orca.OrcaOobUtil;
import io.grpc.xds.orca.OrcaPerRequestUtil;
import io.grpc.xds.shaded.com.github.xds.data.orca.v3.OrcaLoadReport;
import io.grpc.xds.shaded.com.github.xds.service.orca.v3.OpenRcaServiceGrpc;
import io.grpc.xds.shaded.com.github.xds.service.orca.v3.OrcaLoadReportRequest;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.core.v3.Node;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusDiscoveryServiceGrpc;
import io.grpc.xds.shaded.io.envoyproxy.envoy.service.status.v3.ClientStatusRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class Grpc_xdsTest {
    @Test
    void xdsCredentialsWrapFallbackCredentialsAndValidateNulls() {
        ChannelCredentials fallbackChannelCredentials = InsecureChannelCredentials.create();
        ChannelCredentials xdsChannelCredentials = XdsChannelCredentials.create(fallbackChannelCredentials);

        assertThat(xdsChannelCredentials).isNotNull();
        assertThat(xdsChannelCredentials.withoutBearerTokens()).isNotNull();
        assertThatNullPointerException().isThrownBy(() -> XdsChannelCredentials.create(null));

        ServerCredentials fallbackServerCredentials = InsecureServerCredentials.create();
        ServerCredentials xdsServerCredentials = XdsServerCredentials.create(fallbackServerCredentials);

        assertThat(xdsServerCredentials).isNotNull();
        assertThatNullPointerException().isThrownBy(() -> XdsServerCredentials.create(null));
    }

    @Test
    void xdsProvidersAreDiscoverableFromGrpcRegistries() {
        LoadBalancerRegistry loadBalancerRegistry = LoadBalancerRegistry.getDefaultRegistry();

        assertRegisteredProvider(loadBalancerRegistry, new RingHashLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new LeastRequestLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new WeightedRoundRobinLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new CdsLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new ClusterResolverLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new ClusterImplLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new PriorityLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new WeightedTargetLoadBalancerProvider());
        assertRegisteredProvider(loadBalancerRegistry, new WrrLocalityLoadBalancerProvider());

        NameResolverProvider xdsProvider = NameResolverRegistry.getDefaultRegistry().getProviderForScheme("xds");
        assertThat(xdsProvider).isInstanceOf(XdsNameResolverProvider.class);
        assertThat(xdsProvider.getDefaultScheme()).isEqualTo("xds");
    }

    @Test
    void loadBalancerProvidersParsePublicServiceConfigShapes() {
        assertConfigAccepted(new RingHashLoadBalancerProvider(), Map.of("minRingSize", 128.0, "maxRingSize", 4096.0));
        assertConfigAccepted(new LeastRequestLoadBalancerProvider(), Map.of("choiceCount", 3.0));
        assertConfigAccepted(
                new WeightedRoundRobinLoadBalancerProvider(),
                Map.of(
                        "blackoutPeriod", "1s",
                        "weightExpirationPeriod", "3s",
                        "enableOobLoadReport", true,
                        "oobReportingPeriod", "2s",
                        "weightUpdatePeriod", "0.250s",
                        "errorUtilizationPenalty", 1.5f));
        assertConfigAccepted(new CdsLoadBalancerProvider(), Map.of("cluster", "payments-cluster"));

        assertConfigRejected(new RingHashLoadBalancerProvider(), Map.of("minRingSize", 8.0, "maxRingSize", 4.0));
        assertConfigRejected(new LeastRequestLoadBalancerProvider(), Map.of("choiceCount", 1.0));
        assertConfigRejected(new WeightedTargetLoadBalancerProvider(), Map.of("targets", Map.of()));
        assertConfigRejected(new WrrLocalityLoadBalancerProvider(), Map.of("childPolicy", List.of()));
        assertConfigRejected(new PriorityLoadBalancerProvider(), Map.of());
        assertConfigRejected(new ClusterResolverLoadBalancerProvider(), Map.of());
        assertConfigRejected(new ClusterImplLoadBalancerProvider(), Map.of());
    }

    @Test
    void csdsServiceBindsExpectedXdsStatusMethods() {
        CsdsService service = CsdsService.newInstance();
        ServerServiceDefinition definition = service.bindService();

        assertThat(definition.getServiceDescriptor().getName())
                .isEqualTo(ClientStatusDiscoveryServiceGrpc.SERVICE_NAME);
        assertThat(methodFullNames(definition.getMethods()))
                .containsExactlyInAnyOrder(
                        ClientStatusDiscoveryServiceGrpc.getFetchClientStatusMethod().getFullMethodName(),
                        ClientStatusDiscoveryServiceGrpc.getStreamClientStatusMethod().getFullMethodName());

        ClientStatusRequest request = ClientStatusRequest.newBuilder()
                .setNode(Node.newBuilder().setId("test-node").setCluster("test-cluster"))
                .setExcludeResourceContents(true)
                .build();
        ClientStatusRequest parsed = request.toBuilder().build();

        assertThat(parsed.getNode().getId()).isEqualTo("test-node");
        assertThat(parsed.getNode().getCluster()).isEqualTo("test-cluster");
        assertThat(parsed.getExcludeResourceContents()).isTrue();
    }

    @Test
    void xdsServerBuilderAcceptsPublicCsdsServiceAndBootstrapOverride() throws Exception {
        ServerCredentials credentials = XdsServerCredentials.create(InsecureServerCredentials.create());
        Server server = XdsServerBuilder.forPort(0, credentials)
                .addService(CsdsService.newInstance())
                .xdsServingStatusListener(new RecordingServingStatusListener())
                .drainGraceTime(1, TimeUnit.SECONDS)
                .overrideBootstrapForTest(bootstrapOverride())
                .build();

        try {
            assertThat(server).isNotNull();
        } finally {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void orcaProtosDescriptorsAndStubFactoriesAreUsable() {
        OrcaLoadReportRequest request = OrcaLoadReportRequest.newBuilder()
                .setReportInterval(Duration.newBuilder().setSeconds(1).build())
                .addRequestCostNames("cpu-cost")
                .addRequestCostNames("memory-cost")
                .build();
        OrcaLoadReportRequest parsedRequest = request.toBuilder().build();

        assertThat(parsedRequest.getReportInterval().getSeconds()).isEqualTo(1);
        assertThat(parsedRequest.getRequestCostNamesList()).containsExactly("cpu-cost", "memory-cost");

        OrcaLoadReport report = OrcaLoadReport.newBuilder()
                .setCpuUtilization(0.25)
                .setMemUtilization(0.50)
                .setRps(42)
                .setRpsFractional(42.5)
                .setEps(1.5)
                .setApplicationUtilization(0.75)
                .putRequestCost("cpu-cost", 2.0)
                .putUtilization("gpu", 0.60)
                .putNamedMetrics("queue-depth", 7.0)
                .build();
        OrcaLoadReport parsedReport = report.toBuilder().build();

        assertThat(parsedReport.getCpuUtilization()).isEqualTo(0.25);
        assertThat(parsedReport.getMemUtilization()).isEqualTo(0.50);
        assertThat(parsedReport.getRps()).isEqualTo(42);
        assertThat(parsedReport.getRpsFractional()).isEqualTo(42.5);
        assertThat(parsedReport.getEps()).isEqualTo(1.5);
        assertThat(parsedReport.getApplicationUtilization()).isEqualTo(0.75);
        assertThat(parsedReport.getRequestCostOrThrow("cpu-cost")).isEqualTo(2.0);
        assertThat(parsedReport.getUtilizationOrThrow("gpu")).isEqualTo(0.60);
        assertThat(parsedReport.getNamedMetricsOrThrow("queue-depth")).isEqualTo(7.0);

        assertThat(OpenRcaServiceGrpc.SERVICE_NAME).isEqualTo("xds.service.orca.v3.OpenRcaService");
        assertThat(OpenRcaServiceGrpc.getStreamCoreMetricsMethod().getType())
                .isEqualTo(MethodDescriptor.MethodType.SERVER_STREAMING);
        assertThat(OpenRcaServiceGrpc.getServiceDescriptor().getMethods())
                .contains(OpenRcaServiceGrpc.getStreamCoreMetricsMethod());

        Channel channel = new NoopChannel();
        assertThat(OpenRcaServiceGrpc.newStub(channel)).isNotNull();
        assertThat(OpenRcaServiceGrpc.newBlockingStub(channel)).isNotNull();
        assertThat(OpenRcaServiceGrpc.newFutureStub(channel)).isNotNull();
    }

    @Test
    void orcaUtilitiesCreateReportingConfigurationAndPerRequestTracers() {
        OrcaOobUtil.OrcaReportingConfig config = OrcaOobUtil.OrcaReportingConfig.newBuilder()
                .setReportInterval(250, TimeUnit.MILLISECONDS)
                .build();

        assertThat(config.getReportIntervalNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(250));
        assertThat(config.toBuilder().build().getReportIntervalNanos()).isEqualTo(config.getReportIntervalNanos());
        assertThat(config.toString()).contains("reportIntervalNanos");

        AtomicInteger delegateCalls = new AtomicInteger();
        ClientStreamTracer.Factory delegateFactory = new ClientStreamTracer.Factory() {
            @Override
            public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
                delegateCalls.incrementAndGet();
                return new ClientStreamTracer() {};
            }
        };
        OrcaPerRequestUtil perRequestUtil = OrcaPerRequestUtil.getInstance();
        ClientStreamTracer.Factory factory = perRequestUtil.newOrcaClientStreamTracerFactory(
                delegateFactory,
                report -> assertThat(report).isNotNull());

        ClientStreamTracer tracer = factory.newClientStreamTracer(
                ClientStreamTracer.StreamInfo.newBuilder().setCallOptions(CallOptions.DEFAULT).build(),
                new Metadata());

        assertThat(tracer).isNotNull();
        assertThat(delegateCalls).hasValue(1);
        assertThat(perRequestUtil.newOrcaClientStreamTracerFactory(report -> assertThat(report).isNotNull()))
                .isNotNull();
    }

    @Test
    void managedChannelBuilderAcceptsXdsChannelCredentials() {
        ChannelCredentials credentials = XdsChannelCredentials.create(InsecureChannelCredentials.create());

        assertThatCode(() -> {
            ManagedChannel channel = Grpc.newChannelBuilderForAddress("localhost", 8080, credentials).build();
            try {
                assertThat(channel).isNotNull();
            } finally {
                channel.shutdownNow();
                channel.awaitTermination(5, TimeUnit.SECONDS);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void bootstrapperParsesXdsBootstrapNodeSecurityAndAuthorityConfiguration() throws Exception {
        Bootstrapper.BootstrapInfo bootstrapInfo = new TestingBootstrapper().bootstrap(Map.of(
                "xds_servers",
                List.of(serverConfig("xds-primary.example.com:443", true)),
                "node",
                Map.of(
                        "id",
                        "bootstrap-node",
                        "cluster",
                        "bootstrap-cluster",
                        "metadata",
                        Map.of("environment", "test", "debug", true),
                        "locality",
                        Map.of("region", "us-central1", "zone", "us-central1-a", "sub_zone", "blue")),
                "certificate_providers",
                Map.of("file-provider", Map.of(
                        "plugin_name",
                        "file_watcher",
                        "config",
                        Map.of("certificate_file", "/tmp/cert.pem", "private_key_file", "/tmp/key.pem"))),
                "server_listener_resource_name_template",
                "grpc/server/%s",
                "client_default_listener_resource_name_template",
                "xdstp://traffic-director/global/envoy.config.listener.v3.Listener/%s",
                "authorities",
                Map.of("traffic-director", Map.of(
                        "client_listener_resource_name_template",
                        "xdstp://traffic-director/envoy.config.listener.v3.Listener/%s",
                        "xds_servers",
                        List.of(serverConfig("authority-xds.example.com:443", false))))));

        Bootstrapper.ServerInfo primaryServer = bootstrapInfo.servers().get(0);
        assertThat(primaryServer.target()).isEqualTo("xds-primary.example.com:443");
        assertThat(primaryServer.ignoreResourceDeletion()).isTrue();
        assertThat(primaryServer.implSpecificConfig())
                .isEqualTo(Map.copyOf(serverConfig("xds-primary.example.com:443", true)));

        EnvoyProtoData.Node xdsNode = bootstrapInfo.node();
        Node envoyNode = xdsNode.toEnvoyProtoNode();
        assertThat(xdsNode.getId()).isEqualTo("bootstrap-node");
        assertThat(envoyNode.getCluster()).isEqualTo("bootstrap-cluster");
        assertThat(envoyNode.getMetadata().getFieldsOrThrow("environment").getStringValue()).isEqualTo("test");
        assertThat(envoyNode.getMetadata().getFieldsOrThrow("debug").getBoolValue()).isTrue();
        assertThat(envoyNode.getLocality().getRegion()).isEqualTo("us-central1");
        assertThat(envoyNode.getLocality().getZone()).isEqualTo("us-central1-a");
        assertThat(envoyNode.getLocality().getSubZone()).isEqualTo("blue");
        assertThat(envoyNode.getClientFeaturesList())
                .contains(
                        BootstrapperImpl.CLIENT_FEATURE_DISABLE_OVERPROVISIONING,
                        BootstrapperImpl.CLIENT_FEATURE_RESOURCE_IN_SOTW);

        Bootstrapper.CertificateProviderInfo certificateProvider = bootstrapInfo.certProviders().get("file-provider");
        assertThat(certificateProvider.pluginName()).isEqualTo("file_watcher");
        assertThat(certificateProvider.config().get("certificate_file")).isEqualTo("/tmp/cert.pem");

        assertThat(bootstrapInfo.serverListenerResourceNameTemplate()).isEqualTo("grpc/server/%s");
        assertThat(bootstrapInfo.clientDefaultListenerResourceNameTemplate())
                .isEqualTo("xdstp://traffic-director/global/envoy.config.listener.v3.Listener/%s");

        Bootstrapper.AuthorityInfo authority = bootstrapInfo.authorities().get("traffic-director");
        assertThat(authority.clientListenerResourceNameTemplate())
                .isEqualTo("xdstp://traffic-director/envoy.config.listener.v3.Listener/%s");
        assertThat(authority.xdsServers()).hasSize(1);
        assertThat(authority.xdsServers().get(0).target()).isEqualTo("authority-xds.example.com:443");
        assertThat(authority.xdsServers().get(0).ignoreResourceDeletion()).isFalse();
    }

    private static void assertRegisteredProvider(LoadBalancerRegistry registry, LoadBalancerProvider provider) {
        LoadBalancerProvider registeredProvider = registry.getProvider(provider.getPolicyName());

        assertThat(registeredProvider).isNotNull();
        assertThat(registeredProvider.isAvailable()).isTrue();
        assertThat(registeredProvider.getPolicyName()).isEqualTo(provider.getPolicyName());
    }

    private static void assertConfigAccepted(LoadBalancerProvider provider, Map<String, ?> config) {
        NameResolver.ConfigOrError result = provider.parseLoadBalancingPolicyConfig(config);

        assertThat(result.getError()).isNull();
        assertThat(result.getConfig()).isNotNull();
    }

    private static void assertConfigRejected(LoadBalancerProvider provider, Map<String, ?> config) {
        NameResolver.ConfigOrError result = provider.parseLoadBalancingPolicyConfig(config);

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getCode()).isNotEqualTo(Status.Code.OK);
    }

    private static Set<String> methodFullNames(Collection<ServerMethodDefinition<?, ?>> methods) {
        Map<String, Boolean> fullNames = new HashMap<>();
        for (ServerMethodDefinition<?, ?> method : methods) {
            fullNames.put(method.getMethodDescriptor().getFullMethodName(), true);
        }
        return fullNames.keySet();
    }

    private static Map<String, ?> bootstrapOverride() {
        return Map.of(
                "xds_servers",
                List.of(Map.of(
                        "server_uri", "trafficdirector.googleapis.com:443",
                        "channel_creds", List.of(Map.of("type", "insecure")))),
                "node",
                Map.of("id", "test-node", "cluster", "test-cluster"));
    }

    private static Map<String, ?> serverConfig(String serverUri, boolean ignoreResourceDeletion) {
        return Map.of(
                "server_uri",
                serverUri,
                "channel_creds",
                List.of(Map.of("type", "insecure")),
                "server_features",
                ignoreResourceDeletion ? List.of("ignore_resource_deletion") : List.of());
    }

    private static final class TestingBootstrapper extends BootstrapperImpl {
        @Override
        protected String getJsonContent() throws IOException, XdsInitializationException {
            throw new UnsupportedOperationException("bootstrap(Map) supplies configuration directly");
        }

        @Override
        protected Object getImplSpecificConfig(Map<String, ?> rawServerConfig, String serverUri)
                throws XdsInitializationException {
            assertThat(rawServerConfig.get("server_uri")).isEqualTo(serverUri);
            return Map.copyOf(rawServerConfig);
        }
    }

    private static final class RecordingServingStatusListener implements XdsServerBuilder.XdsServingStatusListener {
        private final AtomicInteger statusChanges = new AtomicInteger();

        @Override
        public void onServing() {
            statusChanges.incrementAndGet();
        }

        @Override
        public void onNotServing(Throwable throwable) {
            statusChanges.incrementAndGet();
            assertThat(throwable).isNotNull();
        }
    }

    private static final class NoopChannel extends Channel {
        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            return new ClientCall<>() {
                private boolean halfClosed;

                @Override
                public void start(Listener<ResponseT> responseListener, Metadata headers) {
                    assertThat(responseListener).isNotNull();
                    assertThat(headers).isNotNull();
                }

                @Override
                public void request(int numberOfMessages) {
                    assertThat(numberOfMessages).isPositive();
                }

                @Override
                public void cancel(String message, Throwable cause) {
                    assertThat(halfClosed).isFalse();
                }

                @Override
                public void halfClose() {
                    halfClosed = true;
                }

                @Override
                public void sendMessage(RequestT message) {
                    assertThat(message).isNotNull();
                }
            };
        }

        @Override
        public String authority() {
            return "noop-authority";
        }
    }
}
