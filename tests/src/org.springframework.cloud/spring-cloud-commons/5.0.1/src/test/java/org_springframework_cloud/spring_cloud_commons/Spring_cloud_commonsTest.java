/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_commons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.cloud.client.discovery.simple.InstanceProperties;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.CompletionContext.Status;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultRequestContext;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.HintRequestContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycleValidator;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.ApiVersion;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.HealthCheck;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.Retry;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.Retry.Backoff;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.Stats;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.StickySession;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.Subset;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties.XForwarded;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestAdapter;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.commons.publisher.CloudFlux;
import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtils.HostInfo;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.cloud.configuration.TlsProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import reactor.core.publisher.Flux;

public class Spring_cloud_commonsTest {

    @Test
    void serviceInstancesCreateUrisAndExposeMutableMetadata() {
        Map<String, String> metadata = Map.of("zone", "east", "version", "v1");
        DefaultServiceInstance instance = new DefaultServiceInstance("inventory-1", "inventory",
                "inventory.example", 8080, false, metadata);
        DefaultServiceInstance same = new DefaultServiceInstance("inventory-1", "inventory", "inventory.example", 8080,
                false, metadata);

        assertThat(instance.getInstanceId()).isEqualTo("inventory-1");
        assertThat(instance.getServiceId()).isEqualTo("inventory");
        assertThat(instance.getUri()).isEqualTo(URI.create("http://inventory.example:8080"));
        assertThat(ServiceInstance.createUri(instance)).isEqualTo(URI.create("http://inventory.example:8080"));
        assertThat(instance.getMetadata()).containsEntry("zone", "east");
        assertThat(instance).isEqualTo(same).hasSameHashCodeAs(same);

        instance.setUri(URI.create("https://inventory-secure.example:9443/api"));
        instance.setInstanceId("inventory-2");
        instance.setServiceId("inventory-secure");

        assertThat(instance.isSecure()).isTrue();
        assertThat(instance.getHost()).isEqualTo("inventory-secure.example");
        assertThat(instance.getPort()).isEqualTo(9443);
        assertThat(instance.getUri()).isEqualTo(URI.create("https://inventory-secure.example:9443"));
        assertThat(instance).isNotEqualTo(same);
        assertThat(instance.toString()).contains("inventory-2", "inventory-secure", "inventory-secure.example");
    }

    @Test
    void simpleDiscoveryClientConvertsConfiguredInstanceProperties() {
        InstanceProperties inventory = new InstanceProperties();
        inventory.setInstanceId("inventory-a");
        inventory.setHost("inventory-a.example");
        inventory.setPort(8081);
        inventory.setMetadata(Map.of("rack", "r1"));

        InstanceProperties billing = new InstanceProperties();
        billing.setInstanceId("billing-a");
        billing.setUri(URI.create("https://billing.example:9443"));
        billing.setMetadata(Map.of("rack", "r2"));

        SimpleDiscoveryProperties properties = new SimpleDiscoveryProperties();
        properties.setOrder(42);
        properties.setInstances(Map.of("inventory", List.of(inventory), "billing", List.of(billing)));
        properties.afterPropertiesSet();

        SimpleDiscoveryClient client = new SimpleDiscoveryClient(properties);

        assertThat(client.description()).isEqualTo("Simple Discovery Client");
        assertThat(client.getOrder()).isEqualTo(42);
        assertThat(client.getServices()).containsExactlyInAnyOrder("inventory", "billing");
        assertThat(client.getInstances("missing")).isEmpty();

        ServiceInstance inventoryInstance = client.getInstances("inventory").get(0);
        ServiceInstance billingInstance = billing.toServiceInstance();

        assertThat(inventory.getServiceId()).isEqualTo("inventory");
        assertThat(inventoryInstance.getServiceId()).isEqualTo("inventory");
        assertThat(inventoryInstance.getUri()).isEqualTo(URI.create("http://inventory-a.example:8081"));
        assertThat(inventoryInstance.getMetadata()).containsEntry("rack", "r1");
        assertThat(billing.getServiceId()).isEqualTo("billing");
        assertThat(billingInstance.isSecure()).isTrue();
        assertThat(billingInstance.getUri()).isEqualTo(URI.create("https://billing.example:9443"));
        assertThat(properties.toString()).contains("inventory", "billing", "order");
    }

    @Test
    void idUtilitiesResolveApplicationAndInstanceIdentifiers() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("cloud", Map.of(
                "spring.cloud.client.hostname", "host-a",
                "spring.application.name", "inventory",
                "spring.profiles.active", "blue",
                "spring.application.index", "3",
                "spring.application.instance_id", "instance-3",
                "server.port", "8080",
                "vcap.application.instance_id", "vcap-instance")));

        assertThat(IdUtils.getDefaultInstanceId(environment)).isEqualTo("vcap-instance");
        assertThat(IdUtils.getResolvedServiceId(environment)).isEqualTo("inventory:blue:3:vcap-instance");
        assertThat(IdUtils.combineParts("host-a", ":", "inventory")).isEqualTo("host-a:inventory");
        assertThat(IdUtils.combineParts("host-a", ":", null)).isEqualTo("host-a");
        assertThat(IdUtils.combineParts(null, ":", "inventory")).isEqualTo("inventory");
        assertThat(IdUtils.combineParts(null, ":", null)).isNull();

        StandardEnvironment fallback = new StandardEnvironment();
        fallback.getPropertySources().addFirst(new MapPropertySource("fallback", Map.of(
                "spring.cloud.client.hostname", "host-b",
                "spring.application.name", "orders",
                "server.port", "9090")));

        assertThat(IdUtils.getDefaultInstanceId(fallback)).isEqualTo("host-b:orders:9090");
        assertThat(IdUtils.getDefaultInstanceId(fallback, false)).isEqualTo("orders:9090");
        assertThat(IdUtils.getResolvedServiceId(fallback)).startsWith("orders:9090:");
        assertThat(IdUtils.getUnresolvedServiceId()).contains("spring.application.name");
        assertThat(IdUtils.getUnresolvedServiceIdWithActiveProfiles()).contains("spring.profiles.active");
    }

    @Test
    void loadBalancerUriToolsReconstructsServiceUrisAndPreservesEncodedComponents() {
        DefaultServiceInstance secure = new DefaultServiceInstance("orders-1", "orders", "orders.internal", -1,
                true);
        URI original = URI.create("http://placeholder/items/a%20b?tag=red%20blue#part%201");

        URI reconstructed = LoadBalancerUriTools.reconstructURI(secure, original);

        URI expected = URI.create("https://orders.internal:443/items/a%20b?tag=red%20blue#part%201");
        assertThat(reconstructed).isEqualTo(expected);
        assertThat(LoadBalancerUriTools.constructInterfaceClientsBaseUrl("inventory-service"))
                .isEqualTo(URI.create("http://inventory-service"));
        assertThatThrownBy(() -> LoadBalancerUriTools.reconstructURI(null, original))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Service Instance cannot be null");

        ServiceInstance websocket = new SchemeServiceInstance("gateway", "gateway.internal", 9000, true, "wss");

        assertThat(LoadBalancerUriTools.reconstructURI(websocket, URI.create("ws://placeholder/socket")))
                .isEqualTo(URI.create("wss://gateway.internal:9000/socket"));
    }

    @Test
    void loadBalancerRequestsResponsesAndCompletionContextsCarryTypedState() throws Exception {
        DefaultServiceInstance instance = new DefaultServiceInstance("payments-1", "payments", "payments.internal",
                8080, false);
        DefaultRequest<String> request = new DefaultRequest<>("initial-context");
        request.setContext("request-context");
        DefaultResponse response = new DefaultResponse(instance);
        IllegalStateException failure = new IllegalStateException("downstream failed");

        CompletionContext<String, ServiceInstance, String> completion = new CompletionContext<>(Status.FAILED, failure,
                request, response, "client-response");
        LoadBalancerRequestAdapter<String, String> adapter = new LoadBalancerRequestAdapter<>(
                serviceInstance -> serviceInstance.getServiceId() + "@" + serviceInstance.getHost(), "adapter-context");

        assertThat(request.getContext()).isEqualTo("request-context");
        assertThat(new DefaultRequest<>("request-context")).isEqualTo(request);
        assertThat(response.hasServer()).isTrue();
        assertThat(response.getServer()).isSameAs(instance);
        assertThat(new EmptyResponse().hasServer()).isFalse();
        assertThat(new EmptyResponse().getServer()).isNull();
        assertThat(completion.status()).isEqualTo(Status.FAILED);
        assertThat(completion.getThrowable()).isSameAs(failure);
        assertThat(completion.getLoadBalancerRequest()).isSameAs(request);
        assertThat(completion.getLoadBalancerResponse()).isSameAs(response);
        assertThat(completion.getClientResponse()).isEqualTo("client-response");
        assertThat(adapter.getContext()).isEqualTo("adapter-context");
        assertThat(adapter.apply(instance)).isEqualTo("payments@payments.internal");
    }

    @Test
    void loadBalancerContextsPropertiesAndLifecycleValidationExposeConfigurationState() {
        HintRequestContext hint = new HintRequestContext("blue");
        hint.setRequestStartTime(123L);
        DefaultRequestContext context = new DefaultRequestContext("client-request", "green");
        LoadBalancerProperties properties = new LoadBalancerProperties();
        Retry retry = properties.getRetry();
        Backoff backoff = retry.getBackoff();
        HealthCheck healthCheck = properties.getHealthCheck();
        StickySession stickySession = properties.getStickySession();
        XForwarded xForwarded = properties.getXForwarded();
        Subset subset = properties.getSubset();
        Stats stats = properties.getStats();
        ApiVersion apiVersion = properties.getApiVersion();

        retry.setEnabled(true);
        retry.setRetryOnAllOperations(true);
        retry.setMaxRetriesOnSameServiceInstance(2);
        retry.setMaxRetriesOnNextServiceInstance(3);
        retry.setRetryableStatusCodes(Set.of(429, 503));
        retry.setRetryableExceptions(new LinkedHashSet<>(Set.<Class<? extends Throwable>>of(IllegalStateException.class)));
        retry.setRetryOnAllExceptions(false);
        backoff.setEnabled(true);
        backoff.setMinBackoff(Duration.ofSeconds(10));
        backoff.setMaxBackoff(Duration.ofSeconds(20));
        backoff.setJitter(0.25d);
        healthCheck.setInitialDelay(Duration.ofSeconds(10));
        healthCheck.setInterval(Duration.ofSeconds(15));
        healthCheck.setRefetchInstances(true);
        healthCheck.setRefetchInstancesInterval(Duration.ofSeconds(30));
        healthCheck.setPath(Map.of("inventory", "/actuator/health"));
        healthCheck.setPort(8081);
        healthCheck.setRepeatHealthCheck(false);
        healthCheck.setUpdateResultsList(false);
        stickySession.setAddServiceInstanceCookie(true);
        stickySession.setInstanceIdCookieName("INSTANCE");
        xForwarded.setEnabled(true);
        subset.setInstanceId("node-a");
        subset.setSize(5);
        stats.setIncludePath(true);
        apiVersion.setRequired(true);
        apiVersion.setDefaultVersion("2026-01-01");
        apiVersion.setHeader("X-API-Version");
        apiVersion.setQueryParameter("api-version");
        apiVersion.setPathSegment(1);
        apiVersion.setMediaTypeParameters(Map.of(MediaType.APPLICATION_JSON, "v"));
        apiVersion.setFallbackToAvailableInstances(true);
        properties.setHint(Map.of("inventory", "blue"));
        properties.setHintHeaderName("X-SC-LB-Hint");
        properties.setCallGetWithRequestOnDelegates(false);

        RecordingLifecycle matching = new RecordingLifecycle(true);
        RecordingLifecycle rejected = new RecordingLifecycle(false);

        assertThat(hint.getHint()).isEqualTo("blue");
        assertThat(hint.getRequestStartTime()).isEqualTo(123L);
        assertThat(context.getClientRequest()).isEqualTo("client-request");
        assertThat(context.getHint()).isEqualTo("green");
        assertThat(retry.isEnabled()).isTrue();
        assertThat(retry.isRetryOnAllOperations()).isTrue();
        assertThat(retry.getMaxRetriesOnSameServiceInstance()).isEqualTo(2);
        assertThat(retry.getMaxRetriesOnNextServiceInstance()).isEqualTo(3);
        assertThat(retry.getRetryableStatusCodes()).containsExactlyInAnyOrder(429, 503);
        assertThat(retry.getRetryableExceptions()).contains(IllegalStateException.class);
        assertThat(retry.isRetryOnAllExceptions()).isFalse();
        assertThat(backoff.getMinBackoff()).isEqualTo(Duration.ofSeconds(10));
        assertThat(backoff.getMaxBackoff()).isEqualTo(Duration.ofSeconds(20));
        assertThat(backoff.getJitter()).isEqualTo(0.25d);
        assertThat(healthCheck.getPath()).containsEntry("inventory", "/actuator/health");
        assertThat(healthCheck.getPort()).isEqualTo(8081);
        assertThat(healthCheck.getRepeatHealthCheck()).isFalse();
        assertThat(healthCheck.isUpdateResultsList()).isFalse();
        assertThat(stickySession.getInstanceIdCookieName()).isEqualTo("INSTANCE");
        assertThat(stickySession.isAddServiceInstanceCookie()).isTrue();
        assertThat(xForwarded.isEnabled()).isTrue();
        assertThat(subset.getInstanceId()).isEqualTo("node-a");
        assertThat(subset.getSize()).isEqualTo(5);
        assertThat(stats.isIncludePath()).isTrue();
        assertThat(apiVersion.getMediaTypeParameters()).containsEntry(MediaType.APPLICATION_JSON, "v");
        assertThat(apiVersion.isFallbackToAvailableInstances()).isTrue();
        assertThat(properties.getHint()).containsEntry("inventory", "blue");
        assertThat(properties.getHintHeaderName()).isEqualTo("X-SC-LB-Hint");
        assertThat(properties.isCallGetWithRequestOnDelegates()).isFalse();
        assertThat(LoadBalancerLifecycleValidator.getSupportedLifecycleProcessors(
                Map.<String, LoadBalancerLifecycle>of("matching", matching, "rejected", rejected), String.class,
                String.class, ServiceInstance.class)).containsExactlyInAnyOrder(matching);
        assertThat(LoadBalancerLifecycleValidator.getSupportedLifecycleProcessors(null, String.class, String.class,
                ServiceInstance.class)).isEmpty();
    }

    @Test
    void circuitBreakerDefaultAndFallbackContractsAreApplied() {
        RecordingCircuitBreaker circuitBreaker = new RecordingCircuitBreaker();

        assertThat(circuitBreaker.run(() -> "primary")).isEqualTo("primary");
        String fallbackResult = circuitBreaker.<String>run(() -> {
            throw new IllegalArgumentException("primary failed");
        }, throwable -> "fallback: " + throwable.getMessage());

        assertThat(fallbackResult).isEqualTo("fallback: primary failed");
        assertThatThrownBy(() -> circuitBreaker.run(() -> {
            throw new IllegalStateException("no fallback path");
        })).isInstanceOf(NoFallbackAvailableException.class)
                .hasMessage("No fallback available.")
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("no fallback path");
    }

    @Test
    void cloudFluxEmitsFirstNonEmptyPublisher() {
        List<String> values = CloudFlux.firstNonEmpty(List.of(Flux.<String>empty(), Flux.just("alpha", "bravo"),
                Flux.just("charlie"))).collectList().block(Duration.ofSeconds(10));

        assertThat(values).containsExactly("alpha", "bravo");
        assertThat(CloudFlux.firstNonEmpty(Flux.<String>empty(), Flux.just("single")).blockLast(Duration.ofSeconds(10)))
                .isEqualTo("single");
    }

    @Test
    void inetAndTlsPropertiesExposeNetworkAndStoreConfiguration() throws Exception {
        InetUtilsProperties inetProperties = new InetUtilsProperties();
        inetProperties.setDefaultHostname("fallback-host");
        inetProperties.setDefaultIpAddress("192.0.2.10");
        inetProperties.setTimeoutSeconds(10);
        inetProperties.setIgnoredInterfaces(List.of("docker.*"));
        inetProperties.setPreferredNetworks(List.of("192.0.2"));
        inetProperties.setUseOnlySiteLocalInterfaces(true);

        try (InetUtils inetUtils = new InetUtils(inetProperties)) {
            HostInfo hostInfo = inetUtils.convertAddress(InetAddress.getByName("127.0.0.1"));

            assertThat(hostInfo.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(hostInfo.getIpAddressAsInt()).isEqualTo(2130706433);
        }

        TlsProperties tls = new TlsProperties();
        tls.setEnabled(true);
        tls.setKeyStore(new NamedByteArrayResource("client.p12"));
        tls.setTrustStore(new NamedByteArrayResource("trust.jks"));
        tls.setKeyStorePassword("store-secret");
        tls.setKeyPassword("key-secret");
        tls.setTrustStorePassword("trust-secret");

        assertThat(inetProperties.getDefaultHostname()).isEqualTo("fallback-host");
        assertThat(inetProperties.getDefaultIpAddress()).isEqualTo("192.0.2.10");
        assertThat(inetProperties.getIgnoredInterfaces()).containsExactly("docker.*");
        assertThat(inetProperties.getPreferredNetworks()).containsExactly("192.0.2");
        assertThat(inetProperties.isUseOnlySiteLocalInterfaces()).isTrue();
        assertThat(tls.isEnabled()).isTrue();
        assertThat(tls.getKeyStoreType()).isEqualTo("PKCS12");
        assertThat(tls.getTrustStoreType()).isEqualTo("JKS");
        assertThat(tls.keyStorePassword()).containsExactly('s', 't', 'o', 'r', 'e', '-', 's', 'e', 'c', 'r', 'e', 't');
        assertThat(tls.keyPassword()).containsExactly('k', 'e', 'y', '-', 's', 'e', 'c', 'r', 'e', 't');
        assertThat(tls.trustStorePassword())
                .containsExactly('t', 'r', 'u', 's', 't', '-', 's', 'e', 'c', 'r', 'e', 't');
    }

    private static final class SchemeServiceInstance implements ServiceInstance {

        private final String serviceId;

        private final String host;

        private final int port;

        private final boolean secure;

        private final String scheme;

        private SchemeServiceInstance(String serviceId, String host, int port, boolean secure, String scheme) {
            this.serviceId = serviceId;
            this.host = host;
            this.port = port;
            this.secure = secure;
            this.scheme = scheme;
        }

        @Override
        public String getServiceId() {
            return this.serviceId;
        }

        @Override
        public String getHost() {
            return this.host;
        }

        @Override
        public int getPort() {
            return this.port;
        }

        @Override
        public boolean isSecure() {
            return this.secure;
        }

        @Override
        public URI getUri() {
            return ServiceInstance.createUri(this);
        }

        @Override
        public Map<String, String> getMetadata() {
            return Map.of();
        }

        @Override
        public String getScheme() {
            return this.scheme;
        }
    }

    private static final class RecordingLifecycle implements LoadBalancerLifecycle<String, String, ServiceInstance> {

        private final boolean supports;

        private final AtomicReference<Request<String>> startedRequest = new AtomicReference<>();

        private RecordingLifecycle(boolean supports) {
            this.supports = supports;
        }

        @Override
        public boolean supports(Class requestContextClass, Class clientResponseClass, Class serverTypeClass) {
            return this.supports;
        }

        @Override
        public void onStart(Request<String> request) {
            this.startedRequest.set(request);
        }

        @Override
        public void onStartRequest(Request<String> request, Response<ServiceInstance> lbResponse) {
            this.startedRequest.set(request);
        }

        @Override
        public void onComplete(CompletionContext<String, ServiceInstance, String> completionContext) {
            this.startedRequest.set(completionContext.getLoadBalancerRequest());
        }
    }

    private static final class RecordingCircuitBreaker implements CircuitBreaker {

        @Override
        public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
            try {
                return toRun.get();
            } catch (RuntimeException ex) {
                return fallback.apply(ex);
            }
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(String filename) {
            super(new byte[0]);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }
}
