/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_xds;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.protobuf.Duration;
import com.google.protobuf.UInt32Value;
import io.grpc.BindableService;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.LoadBalancerProvider;
import io.grpc.NameResolver.ConfigOrError;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.services.MetricRecorder;
import io.grpc.xds.CdsLoadBalancerProvider;
import io.grpc.xds.ClusterImplLoadBalancerProvider;
import io.grpc.xds.ClusterManagerLoadBalancerProvider;
import io.grpc.xds.ClusterResolverLoadBalancerProvider;
import io.grpc.xds.CsdsService;
import io.grpc.xds.LeastRequestLoadBalancerProvider;
import io.grpc.xds.PriorityLoadBalancerProvider;
import io.grpc.xds.RingHashLoadBalancerProvider;
import io.grpc.xds.RingHashOptions;
import io.grpc.xds.WeightedRoundRobinLoadBalancerProvider;
import io.grpc.xds.WeightedTargetLoadBalancerProvider;
import io.grpc.xds.WrrLocalityLoadBalancerProvider;
import io.grpc.xds.XdsChannelCredentials;
import io.grpc.xds.XdsNameResolverProvider;
import io.grpc.xds.XdsServerCredentials;
import io.grpc.xds.client.Bootstrapper.AuthorityInfo;
import io.grpc.xds.client.Bootstrapper.BootstrapInfo;
import io.grpc.xds.client.Bootstrapper.CertificateProviderInfo;
import io.grpc.xds.client.Bootstrapper.ServerInfo;
import io.grpc.xds.client.EnvoyProtoData.Node;
import io.grpc.xds.client.LoadStatsManager2;
import io.grpc.xds.client.LoadStatsManager2.ClusterDropStats;
import io.grpc.xds.client.LoadStatsManager2.ClusterLocalityStats;
import io.grpc.xds.client.Locality;
import io.grpc.xds.client.Stats.BackendLoadMetricStats;
import io.grpc.xds.client.Stats.ClusterStats;
import io.grpc.xds.client.Stats.DroppedRequests;
import io.grpc.xds.client.Stats.UpstreamLocalityStats;
import io.grpc.xds.orca.OrcaMetricReportingServerInterceptor;
import io.grpc.xds.orca.OrcaOobUtil.OrcaReportingConfig;
import io.grpc.xds.orca.OrcaPerRequestUtil;
import io.grpc.xds.orca.OrcaServiceImpl;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.cluster.v3.Cluster;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.core.v3.Address;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.core.v3.HealthStatus;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.endpoint.v3.Endpoint;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.endpoint.v3.LocalityLbEndpoints;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.route.v3.Route;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.route.v3.RouteAction;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.route.v3.RouteMatch;
import io.grpc.xds.shaded.io.envoyproxy.envoy.config.route.v3.VirtualHost;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Grpc_xdsTest {
    @Test
    void loadBalancerProvidersExposePolicyNamesAndParseServiceConfigs() {
        List<LoadBalancerProvider> providers = List.of(
                new CdsLoadBalancerProvider(),
                new ClusterImplLoadBalancerProvider(),
                new ClusterManagerLoadBalancerProvider(),
                new ClusterResolverLoadBalancerProvider(),
                new LeastRequestLoadBalancerProvider(),
                new PriorityLoadBalancerProvider(),
                new RingHashLoadBalancerProvider(),
                new WeightedRoundRobinLoadBalancerProvider(),
                new WeightedTargetLoadBalancerProvider(),
                new WrrLocalityLoadBalancerProvider());

        assertThat(providers).allSatisfy(provider -> assertThat(provider.getPriority()).isEqualTo(5));
        assertThat(providers).extracting(LoadBalancerProvider::getPolicyName).containsExactly(
                "cds_experimental",
                "cluster_impl_experimental",
                "cluster_manager_experimental",
                "cluster_resolver_experimental",
                "least_request_experimental",
                "priority_experimental",
                "ring_hash_experimental",
                "weighted_round_robin",
                "weighted_target_experimental",
                "wrr_locality_experimental");

        assertValidConfig(new CdsLoadBalancerProvider().parseLoadBalancingPolicyConfig(
                Map.of("cluster", "service-cluster")));
        assertValidConfig(new LeastRequestLoadBalancerProvider().parseLoadBalancingPolicyConfig(
                Map.of("choiceCount", 3.0)));
        assertValidConfig(new RingHashLoadBalancerProvider().parseLoadBalancingPolicyConfig(
                Map.of("minRingSize", 8.0, "maxRingSize", 64.0)));
        assertValidConfig(new WeightedRoundRobinLoadBalancerProvider().parseLoadBalancingPolicyConfig(Map.of()));

        ConfigOrError badRingHash = new RingHashLoadBalancerProvider().parseLoadBalancingPolicyConfig(
                Map.of("minRingSize", 10.0, "maxRingSize", 2.0));
        assertThat(badRingHash.getError()).isNotNull();
        assertThat(badRingHash.getError().getCode()).isEqualTo(Status.Code.UNAVAILABLE);

        ConfigOrError childOnlyPolicy = new PriorityLoadBalancerProvider().parseLoadBalancingPolicyConfig(Map.of());
        assertThat(childOnlyPolicy.getError()).isNotNull();
        assertThat(childOnlyPolicy.getError().getDescription()).contains("cannot be used from service config");
    }

    @Test
    void ringHashGlobalCapCanBeChangedAndRestored() {
        long originalCap = RingHashOptions.getRingSizeCap();
        try {
            RingHashOptions.setRingSizeCap(8192L);
            assertThat(RingHashOptions.getRingSizeCap()).isEqualTo(8192L);

            assertValidConfig(new RingHashLoadBalancerProvider().parseLoadBalancingPolicyConfig(
                    Map.of("minRingSize", 4096.0, "maxRingSize", 16384.0)));
        } finally {
            RingHashOptions.setRingSizeCap(originalCap);
        }
    }

    @Test
    void xdsCredentialsNameResolverAndBindableServicesAreAvailable() {
        assertThat(XdsChannelCredentials.create(InsecureChannelCredentials.create())).isNotNull();
        assertThat(XdsServerCredentials.create(InsecureServerCredentials.create())).isNotNull();

        XdsNameResolverProvider provider = new XdsNameResolverProvider();
        assertThat(provider.getDefaultScheme()).isEqualTo("xds");
        assertThat(provider.getProducedSocketAddressTypes()).containsExactly(InetSocketAddress.class);
        assertThat(XdsNameResolverProvider.createForTest("bootstrap-authority", Map.of("node", Map.of("id", "node-1"))))
                .isNotNull();

        ServerServiceDefinition csdsDefinition = CsdsService.newInstance().bindService();
        assertThat(csdsDefinition.getServiceDescriptor().getName()).contains("ClientStatusDiscoveryService");
        assertThat(csdsDefinition.getMethods()).isNotEmpty();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            MetricRecorder recorder = MetricRecorder.newInstance();
            recorder.setCpuUtilizationMetric(0.5);
            recorder.putUtilizationMetric("queue", 0.25);

            BindableService service = OrcaServiceImpl.createService(executor, recorder, 1L, TimeUnit.SECONDS);
            ServerServiceDefinition orcaDefinition = service.bindService();
            assertThat(orcaDefinition.getServiceDescriptor().getName()).contains("OpenRcaService");
            assertThat(orcaDefinition.getMethods()).isNotEmpty();

            assertThat(OrcaMetricReportingServerInterceptor.getInstance()).isNotNull();
            assertThat(OrcaMetricReportingServerInterceptor.create(recorder)).isNotNull();
            assertThat(OrcaPerRequestUtil.getInstance().newOrcaClientStreamTracerFactory(report -> { })).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void orcaReportingConfigPreservesIntervalUnits() {
        OrcaReportingConfig config = OrcaReportingConfig.newBuilder()
                .setReportInterval(250L, TimeUnit.MILLISECONDS)
                .build();

        assertThat(config.getReportIntervalNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(250L));
        assertThat(config.toBuilder().setReportInterval(1L, TimeUnit.SECONDS).build().getReportIntervalNanos())
                .isEqualTo(TimeUnit.SECONDS.toNanos(1L));
        assertThat(config.toString()).contains("reportIntervalNanos");
    }

    @Test
    void bootstrapAndEnvoyNodeValueObjectsPreserveXdsConfiguration() {
        Locality locality = Locality.create("europe-west1", "zone-a", "rack-7");
        Node node = Node.newBuilder()
                .setId("test-node")
                .setCluster("test-cluster")
                .setLocality(locality)
                .setMetadata(Map.of("stage", "integration", "canary", true))
                .setBuildVersion("test-build")
                .setUserAgentName("reachability-metadata-tests")
                .setUserAgentVersion("1")
                .addClientFeatures("envoy.lb.does_not_support_overprovisioning")
                .build();

        assertThat(node.getId()).isEqualTo("test-node");
        assertThat(node.toBuilder().build()).isEqualTo(node);
        assertThat(node.toEnvoyProtoNode().getCluster()).isEqualTo("test-cluster");
        assertThat(node.toEnvoyProtoNode().getLocality().getRegion()).isEqualTo("europe-west1");
        assertThat(node.toEnvoyProtoNode().getMetadata().getFieldsOrThrow("stage").getStringValue())
                .isEqualTo("integration");
        assertThat(node.toEnvoyProtoNode().getClientFeaturesList())
                .contains("envoy.lb.does_not_support_overprovisioning");

        ServerInfo serverInfo = ServerInfo.create("xds.example.test:443", Map.of("channel_creds", List.of()), true);
        CertificateProviderInfo certificateProvider = CertificateProviderInfo.create(
                "file_watcher", Map.of("certificate_file", "cert.pem", "private_key_file", "key.pem"));
        AuthorityInfo authority = AuthorityInfo.create(
                "xdstp://authority/envoy.config.listener.v3.Listener/%s", List.of(serverInfo));
        BootstrapInfo bootstrap = BootstrapInfo.builder()
                .servers(List.of(serverInfo))
                .node(node)
                .certProviders(Map.of("default", certificateProvider))
                .serverListenerResourceNameTemplate("grpc/server?xds.resource.listening_address=%s")
                .clientDefaultListenerResourceNameTemplate("%s")
                .authorities(Map.of("authority", authority))
                .build();

        assertThat(bootstrap.servers()).containsExactly(serverInfo);
        assertThat(bootstrap.certProviders()).containsEntry("default", certificateProvider);
        assertThat(bootstrap.authorities()).containsEntry("authority", authority);
        assertThat(bootstrap.serverListenerResourceNameTemplate()).contains("listening_address");
        assertThat(authority.clientListenerResourceNameTemplate()).startsWith("xdstp://authority/");
        assertThat(certificateProvider.config().get("certificate_file")).isEqualTo("cert.pem");
    }

    @Test
    void loadStatsManagerReportsDropsLocalityCallsAndBackendMetrics() {
        Supplier<Stopwatch> stopwatchSupplier = Stopwatch::createStarted;
        LoadStatsManager2 manager = new LoadStatsManager2(stopwatchSupplier);
        Locality locality = Locality.create("us-central1", "zone-b", "subzone-c");

        ClusterDropStats dropStats = manager.getClusterDropStats("cluster-a", "eds-service-a");
        ClusterLocalityStats localityStats = manager.getClusterLocalityStats("cluster-a", "eds-service-a", locality);
        try {
            dropStats.recordDroppedRequest("throttle");
            dropStats.recordDroppedRequest();
            localityStats.recordCallStarted();
            localityStats.recordCallFinished(Status.OK);
            localityStats.recordCallStarted();
            localityStats.recordBackendLoadMetricStats(Map.of("application_utilization", 0.75));
            localityStats.recordCallFinished(Status.UNAVAILABLE);

            List<ClusterStats> reports = manager.getClusterStatsReports("cluster-a");
            assertThat(reports).hasSize(1);
            ClusterStats report = reports.get(0);

            assertThat(report.clusterServiceName()).isEqualTo("eds-service-a");
            assertThat(report.totalDroppedRequests()).isEqualTo(2L);
            assertThat(report.droppedRequestsList()).singleElement().satisfies(dropped -> {
                assertThat(dropped.category()).isEqualTo("throttle");
                assertThat(dropped.droppedCount()).isEqualTo(1L);
            });
            assertThat(report.upstreamLocalityStatsList()).singleElement().satisfies(upstream -> {
                assertThat(upstream.locality()).isEqualTo(locality);
                assertThat(upstream.totalIssuedRequests()).isEqualTo(2L);
                assertThat(upstream.totalSuccessfulRequests()).isEqualTo(1L);
                assertThat(upstream.totalErrorRequests()).isEqualTo(1L);
                assertThat(upstream.totalRequestsInProgress()).isZero();
                Map<String, BackendLoadMetricStats> metrics = upstream.loadMetricStatsMap();
                assertThat(metrics).containsOnlyKeys("application_utilization");
                assertThat(metrics.get("application_utilization").numRequestsFinishedWithMetric()).isEqualTo(1L);
                assertThat(metrics.get("application_utilization").totalMetricValue()).isEqualTo(0.75);
            });

            assertThat(manager.getClusterStatsReports("cluster-a").get(0).totalDroppedRequests()).isZero();
        } finally {
            dropStats.release();
            localityStats.release();
        }
    }

    @Test
    void statsValueTypesCanBeCreatedWithoutManager() {
        Locality locality = Locality.create("region", "zone", "subzone");
        UpstreamLocalityStats upstream = UpstreamLocalityStats.create(
                locality, 3L, 2L, 1L, 0L, Map.of());
        DroppedRequests droppedRequests = DroppedRequests.create("overload", 5L);

        assertThat(upstream.locality()).isEqualTo(locality);
        assertThat(upstream.totalIssuedRequests()).isEqualTo(3L);
        assertThat(upstream.totalSuccessfulRequests()).isEqualTo(2L);
        assertThat(upstream.totalErrorRequests()).isEqualTo(1L);
        assertThat(upstream.totalRequestsInProgress()).isZero();
        assertThat(upstream.loadMetricStatsMap()).isEmpty();
        assertThat(droppedRequests.category()).isEqualTo("overload");
        assertThat(droppedRequests.droppedCount()).isEqualTo(5L);
    }

    @Test
    void shadedEnvoyDiscoveryMessagesBuildRoutesClustersAndEndpoints() {
        Route route = Route.newBuilder()
                .setName("default-route")
                .setMatch(RouteMatch.newBuilder().setPrefix("/"))
                .setRoute(RouteAction.newBuilder().setCluster("cluster-a"))
                .build();
        RouteConfiguration routeConfiguration = RouteConfiguration.newBuilder()
                .setName("listener-route")
                .addVirtualHosts(VirtualHost.newBuilder()
                        .setName("virtual-host")
                        .addDomains("example.test")
                        .addDomains("*.example.test")
                        .addRoutes(route))
                .build();

        Cluster cluster = Cluster.newBuilder()
                .setName("cluster-a")
                .setType(Cluster.DiscoveryType.EDS)
                .setEdsClusterConfig(Cluster.EdsClusterConfig.newBuilder().setServiceName("eds-service-a"))
                .setConnectTimeout(Duration.newBuilder().setSeconds(2L))
                .build();

        SocketAddress socketAddress = SocketAddress.newBuilder()
                .setProtocol(SocketAddress.Protocol.TCP)
                .setAddress("127.0.0.1")
                .setPortValue(8080)
                .build();
        LbEndpoint lbEndpoint = LbEndpoint.newBuilder()
                .setEndpoint(Endpoint.newBuilder().setAddress(Address.newBuilder().setSocketAddress(socketAddress)))
                .setHealthStatus(HealthStatus.HEALTHY)
                .setLoadBalancingWeight(UInt32Value.of(2))
                .build();
        LocalityLbEndpoints localityLbEndpoints = LocalityLbEndpoints.newBuilder()
                .setLocality(io.grpc.xds.shaded.io.envoyproxy.envoy.config.core.v3.Locality.newBuilder()
                        .setRegion("region")
                        .setZone("zone")
                        .setSubZone("subzone"))
                .addLbEndpoints(lbEndpoint)
                .setLoadBalancingWeight(UInt32Value.of(10))
                .setPriority(1)
                .build();
        ClusterLoadAssignment assignment = ClusterLoadAssignment.newBuilder()
                .setClusterName("cluster-a")
                .addEndpoints(localityLbEndpoints)
                .build();

        assertThat(RouteConfiguration.getDescriptor().getFullName())
                .isEqualTo("envoy.config.route.v3.RouteConfiguration");
        assertThat(routeConfiguration.getVirtualHosts(0).getDomainsList())
                .containsExactly("example.test", "*.example.test");
        assertThat(routeConfiguration.getVirtualHosts(0).getRoutes(0).getRoute().getCluster()).isEqualTo("cluster-a");
        assertThat(cluster.getType()).isEqualTo(Cluster.DiscoveryType.EDS);
        assertThat(cluster.getEdsClusterConfig().getServiceName()).isEqualTo("eds-service-a");
        assertThat(assignment.getEndpoints(0).getLocality().getRegion()).isEqualTo("region");
        assertThat(assignment.getEndpoints(0).getLbEndpoints(0).getHealthStatus()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(assignment.getEndpoints(0).getLbEndpoints(0).getEndpoint().getAddress().getSocketAddress())
                .isEqualTo(socketAddress);
    }

    private static void assertValidConfig(ConfigOrError configOrError) {
        assertThat(configOrError.getError()).isNull();
        assertThat(configOrError.getConfig()).isNotNull();
    }
}
