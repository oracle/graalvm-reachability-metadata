/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_commons;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.InstanceProperties;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.simple.reactive.SimpleReactiveDiscoveryProperties;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.DefaultRequestContext;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.HintRequestContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.ResponseData;
import org.springframework.cloud.commons.publisher.CloudFlux;
import org.springframework.cloud.commons.util.IdUtils;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.cloud.configuration.TlsProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.groups.Tuple.tuple;

public class Spring_cloud_commonsTest {

    @Test
    void defaultServiceInstanceCreatesUrisAndPreservesMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("zone", "eu-central-1a");
        metadata.put("version", "v2");

        DefaultServiceInstance instance = new DefaultServiceInstance("orders-1", "orders", "orders.example", 8080,
                false, metadata);

        assertThat(instance.getUri()).isEqualTo(URI.create("http://orders.example:8080"));
        assertThat(ServiceInstance.createUri(instance)).isEqualTo(instance.getUri());
        assertThat(instance.getMetadata()).containsEntry("zone", "eu-central-1a");
        assertThat(instance.toString()).contains("orders-1", "orders.example", "8080");

        instance.setUri(URI.create("https://secure-orders.example:9443/actuator/health"));

        assertThat(instance.getHost()).isEqualTo("secure-orders.example");
        assertThat(instance.getPort()).isEqualTo(9443);
        assertThat(instance.isSecure()).isTrue();
        assertThat(instance.getUri()).isEqualTo(URI.create("https://secure-orders.example:9443"));
    }

    @Test
    void simpleDiscoveryClientReadsServiceInstancesFromProperties() throws Exception {
        InstanceProperties primary = instance("orders-1", "http://orders-one.example:8080", Map.of("zone", "a"));
        InstanceProperties secondary = instance("orders-2", "https://orders-two.example:8443", Map.of("zone", "b"));
        SimpleDiscoveryProperties properties = new SimpleDiscoveryProperties();
        properties.setOrder(5);
        properties.setInstances(new LinkedHashMap<>(Map.of("orders", List.of(primary, secondary))));

        properties.afterPropertiesSet();
        SimpleDiscoveryClient discoveryClient = new SimpleDiscoveryClient(properties);

        assertThat(discoveryClient.description()).isEqualTo("Simple Discovery Client");
        assertThat(discoveryClient.getOrder()).isEqualTo(5);
        assertThat(discoveryClient.getServices()).containsExactly("orders");
        assertThat(discoveryClient.getInstances("missing")).isEmpty();
        assertThat(discoveryClient.getInstances("orders"))
            .extracting(ServiceInstance::getInstanceId, ServiceInstance::getServiceId, ServiceInstance::getHost,
                    ServiceInstance::getPort, ServiceInstance::isSecure)
            .containsExactly(
                    tuple("orders-1", "orders", "orders-one.example", 8080, false),
                    tuple("orders-2", "orders", "orders-two.example", 8443, true));
    }

    @Test
    void simpleReactiveDiscoveryClientPublishesConfiguredServiceInstances() throws Exception {
        InstanceProperties payment = instance("payments-1", "https://payments.example:9443", Map.of("region", "emea"));
        SimpleReactiveDiscoveryProperties properties = new SimpleReactiveDiscoveryProperties();
        properties.setOrder(7);
        properties.setInstances(new LinkedHashMap<>(Map.of("payments", List.of(payment))));

        properties.afterPropertiesSet();
        SimpleReactiveDiscoveryClient discoveryClient = new SimpleReactiveDiscoveryClient(properties);
        List<String> services = discoveryClient.getServices().collectList().block(Duration.ofSeconds(1));
        List<ServiceInstance> instances = discoveryClient.getInstances("payments")
            .collectList()
            .block(Duration.ofSeconds(1));

        assertThat(discoveryClient.description()).isEqualTo("Simple Reactive Discovery Client");
        assertThat(discoveryClient.getOrder()).isEqualTo(7);
        assertThat(services).containsExactly("payments");
        assertThat(instances).hasSize(1);
        ServiceInstance serviceInstance = instances.get(0);
        assertThat(serviceInstance.getInstanceId()).isEqualTo("payments-1");
        assertThat(serviceInstance.getServiceId()).isEqualTo("payments");
        assertThat(serviceInstance.getHost()).isEqualTo("payments.example");
        assertThat(serviceInstance.getPort()).isEqualTo(9443);
        assertThat(serviceInstance.isSecure()).isTrue();
    }

    @Test
    void compositeDiscoveryClientSortsDelegatesAndReturnsFirstNonEmptyResult() {
        DefaultServiceInstance inventory = new DefaultServiceInstance("inventory-1", "inventory", "inventory.example",
                9000, false);
        RecordingDiscoveryClient slowEmptyClient = new RecordingDiscoveryClient(20, List.of("orders"), Map.of());
        RecordingDiscoveryClient fastClient = new RecordingDiscoveryClient(-10, List.of("inventory"),
                Map.of("inventory", List.of(inventory)));
        List<DiscoveryClient> delegates = new ArrayList<>(List.of(slowEmptyClient, fastClient));

        CompositeDiscoveryClient composite = new CompositeDiscoveryClient(delegates);

        assertThat(composite.description()).isEqualTo("Composite Discovery Client");
        assertThat(composite.getDiscoveryClients()).containsExactly(fastClient, slowEmptyClient);
        assertThat(composite.getServices()).containsExactly("inventory", "orders");
        assertThat(composite.getInstances("inventory")).containsExactly(inventory);

        composite.probe();

        assertThat(fastClient.probeCount()).isEqualTo(1);
        assertThat(slowEmptyClient.probeCount()).isEqualTo(1);
    }

    @Test
    void loadBalancerUriToolsReconstructsEncodedUrisForSelectedServiceInstance() {
        ServiceInstance selected = new DefaultServiceInstance("inventory-1", "inventory", "inventory.example", -1,
                true);
        URI original = URI.create("http://inventory/api/items/a%20b?tag=x%2By#frag%2Fment");

        URI reconstructed = LoadBalancerUriTools.reconstructURI(selected, original);

        assertThat(reconstructed)
            .isEqualTo(URI.create("https://inventory.example:443/api/items/a%20b?tag=x%2By#frag%2Fment"));
        assertThat(LoadBalancerUriTools.constructInterfaceClientsBaseUrl("orders"))
            .isEqualTo(URI.create("http://orders"));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> LoadBalancerUriTools.reconstructURI(null, original))
            .withMessage("Service Instance cannot be null.");
    }

    @Test
    void loadBalancerInterceptorExecutesRequestAgainstChosenServiceInstance() throws IOException {
        ServiceInstance selected = new DefaultServiceInstance("orders-1", "orders", "orders.internal", 8081,
                false);
        RecordingLoadBalancerClient loadBalancer = new RecordingLoadBalancerClient(selected);
        LoadBalancerRequestTransformer transformer = (request, instance) -> new HeaderAddingRequest(request,
                "X-Service-Instance", instance.getInstanceId());
        LoadBalancerRequestFactory requestFactory = new LoadBalancerRequestFactory(loadBalancer, List.of(transformer));
        LoadBalancerInterceptor interceptor = new LoadBalancerInterceptor(loadBalancer, requestFactory);
        SimpleHttpRequest request = new SimpleHttpRequest(HttpMethod.PUT, URI.create("http://orders/api/items?x=1"));
        byte[] body = new byte[] { 1, 2, 3 };
        RecordingClientHttpRequestExecution execution = new RecordingClientHttpRequestExecution();

        ClientHttpResponse response = interceptor.intercept(request, body, execution);

        assertThat(loadBalancer.serviceId()).isEqualTo("orders");
        assertThat(execution.request().getURI()).isEqualTo(URI.create("http://orders.internal:8081/api/items?x=1"));
        assertThat(execution.request().getHeaders().getFirst("X-Service-Instance")).isEqualTo("orders-1");
        assertThat(execution.body()).isSameAs(body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void requestAndResponseDataExposeLoadBalancerExchangeState() {
        ClientRequest clientRequest = ClientRequest.create(HttpMethod.POST, URI.create("https://api.example/orders"))
            .header(HttpHeaders.ACCEPT, "application/json")
            .cookie("SESSION", "abc123")
            .attribute("route", "blue")
            .build();
        RequestData requestData = new RequestData(clientRequest);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add("X-Trace-Id", "trace-1");
        MultiValueMap<String, ResponseCookie> responseCookies = new LinkedMultiValueMap<>();
        responseCookies.add("SERVER", ResponseCookie.from("SERVER", "node-a").path("/").build());
        ResponseData responseData = new ResponseData(HttpStatus.ACCEPTED, responseHeaders, responseCookies,
                requestData);

        DefaultRequest<RequestData> loadBalancerRequest = new DefaultRequest<>(requestData);
        DefaultResponse loadBalancerResponse = new DefaultResponse(
                new DefaultServiceInstance("orders-1", "orders", "orders.example", 8080, false));
        CompletionContext<ResponseData, ServiceInstance, RequestData> completionContext = new CompletionContext<>(
                CompletionContext.Status.SUCCESS, loadBalancerRequest, loadBalancerResponse, responseData);

        assertThat(requestData.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(requestData.getUrl()).isEqualTo(URI.create("https://api.example/orders"));
        assertThat(requestData.getHeaders().get(HttpHeaders.ACCEPT)).containsExactly("application/json");
        assertThat(requestData.getCookies()).containsEntry("SESSION", List.of("abc123"));
        assertThat(requestData.getAttributes()).containsEntry("route", "blue");
        assertThat(responseData.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(responseData.getHeaders().get("X-Trace-Id")).containsExactly("trace-1");
        assertThat(responseData.getCookies().getFirst("SERVER").getValue()).isEqualTo("node-a");
        assertThat(completionContext.status()).isEqualTo(CompletionContext.Status.SUCCESS);
        assertThat(completionContext.getLoadBalancerRequest()).isSameAs(loadBalancerRequest);
        assertThat(completionContext.getLoadBalancerResponse()).isSameAs(loadBalancerResponse);
        assertThat(completionContext.getClientResponse()).isSameAs(responseData);
        assertThat(new EmptyResponse().hasServer()).isFalse();
    }

    @Test
    void requestContextsCarryHintsClientRequestsAndStartTimes() {
        HintRequestContext hintContext = new HintRequestContext("gold");
        hintContext.setRequestStartTime(42L);
        DefaultRequestContext requestContext = new DefaultRequestContext("payload", "blue");
        DefaultRequest<String> request = new DefaultRequest<>("first");

        request.setContext("second");

        assertThat(hintContext.getHint()).isEqualTo("gold");
        assertThat(hintContext.getRequestStartTime()).isEqualTo(42L);
        assertThat(hintContext.toString()).contains("gold");
        assertThat(requestContext.getClientRequest()).isEqualTo("payload");
        assertThat(requestContext.getHint()).isEqualTo("blue");
        assertThat(request.getContext()).isEqualTo("second");
        assertThat(request.toString()).contains("second");
    }

    @Test
    void cloudFluxFirstNonEmptyMirrorsTheFirstPublisherThatEmitsAValue() {
        List<String> values = CloudFlux.firstNonEmpty(Flux.empty(), Flux.just("first", "second"), Flux.just("late"))
            .collectList()
            .block(Duration.ofSeconds(1));
        List<String> iterableValues = CloudFlux.firstNonEmpty(List.of(Flux.empty(), Flux.just("iterable")))
            .collectList()
            .block(Duration.ofSeconds(1));

        assertThat(values).containsExactly("first", "second");
        assertThat(iterableValues).containsExactly("iterable");
    }

    @Test
    void idAndInetUtilitiesResolveStableHostIdentifiers() throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.cloud.client.hostname", "host-a");
        properties.put("spring.application.name", "orders");
        properties.put("server.port", "8080");
        properties.put("spring.profiles.active", "test");
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));

        assertThat(IdUtils.getDefaultInstanceId(environment)).isEqualTo("host-a:orders:8080");
        assertThat(IdUtils.getDefaultInstanceId(environment, false)).isEqualTo("orders:8080");
        assertThat(IdUtils.combineParts("left", ":", "right")).isEqualTo("left:right");
        assertThat(IdUtils.getResolvedServiceId(environment)).contains("orders:test");

        InetUtilsProperties inetProperties = new InetUtilsProperties();
        inetProperties.setTimeoutSeconds(1);
        try (InetUtils inetUtils = new InetUtils(inetProperties)) {
            InetUtils.HostInfo hostInfo = inetUtils.convertAddress(InetAddress.getByName("127.0.0.1"));
            hostInfo.setOverride(true);

            assertThat(hostInfo.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(hostInfo.getIpAddressAsInt()).isEqualTo(2130706433);
            assertThat(hostInfo.isOverride()).isTrue();
        }
    }

    @Test
    void circuitBreakerFactoryStoresConfigurationsAndAppliesFallbacks() {
        RecordingCircuitBreakerFactory factory = new RecordingCircuitBreakerFactory();

        factory.configure(builder -> builder.value("configured-" + builder.id()), "alpha", "beta");
        factory.configureDefault(id -> "default-" + id);
        CircuitBreaker circuitBreaker = factory.create("orders");

        assertThat(factory.configurationFor("alpha")).isEqualTo("configured-alpha");
        assertThat(factory.configurationFor("beta")).isEqualTo("configured-beta");
        assertThat(factory.defaultConfigurationFor("orders")).isEqualTo("default-orders");
        assertThat(circuitBreaker.run(() -> "primary", throwable -> "fallback")).isEqualTo("primary");
        String fallbackResult = circuitBreaker.<String>run(() -> {
            throw new IllegalStateException("boom");
        }, throwable -> "fallback-" + throwable.getMessage());
        assertThat(fallbackResult).isEqualTo("fallback-boom");
        assertThatExceptionOfType(NoFallbackAvailableException.class).isThrownBy(() -> circuitBreaker.run(() -> {
            throw new IllegalArgumentException("no fallback");
        })).withMessage("No fallback available.");
    }

    @Test
    void tlsPropertiesInferStoreTypesAndExposePasswords() {
        TlsProperties properties = new TlsProperties();

        properties.setEnabled(true);
        properties.setKeyStore(new NamedByteArrayResource("client-certificate.p12"));
        properties.setTrustStore(new NamedByteArrayResource("trusted-ca.jks"));
        properties.setKeyStorePassword("store-secret");
        properties.setKeyPassword("key-secret");
        properties.setTrustStorePassword("trust-secret");

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getKeyStoreType()).isEqualTo("PKCS12");
        assertThat(properties.getTrustStoreType()).isEqualTo("JKS");
        assertThat(String.valueOf(properties.keyStorePassword())).isEqualTo("store-secret");
        assertThat(String.valueOf(properties.keyPassword())).isEqualTo("key-secret");
        assertThat(String.valueOf(properties.trustStorePassword())).isEqualTo("trust-secret");

        TlsProperties unknownExtension = new TlsProperties();
        unknownExtension.setKeyStore(new NamedByteArrayResource("client-certificate.pem"));

        assertThat(unknownExtension.getKeyStoreType()).isEqualTo("PKCS12");
    }

    private static InstanceProperties instance(String instanceId, String uri, Map<String, String> metadata) {
        InstanceProperties instance = new InstanceProperties();
        instance.setInstanceId(instanceId);
        instance.setUri(URI.create(uri));
        instance.setMetadata(new LinkedHashMap<>(metadata));
        return instance;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(String filename) {
            super(new byte[0]);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }

    }

    private static final class SimpleHttpRequest implements HttpRequest {

        private final HttpMethod method;

        private final URI uri;

        private final HttpHeaders headers = new HttpHeaders();

        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private SimpleHttpRequest(HttpMethod method, URI uri) {
            this.method = method;
            this.uri = uri;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

    }

    private static final class HeaderAddingRequest extends HttpRequestWrapper {

        private final HttpHeaders headers;

        private HeaderAddingRequest(HttpRequest request, String headerName, String headerValue) {
            super(request);
            this.headers = new HttpHeaders();
            this.headers.putAll(request.getHeaders());
            this.headers.add(headerName, headerValue);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

    }

    private static final class SimpleClientHttpResponse implements ClientHttpResponse {

        private final HttpStatusCode statusCode;

        private SimpleClientHttpResponse(HttpStatusCode statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return statusCode;
        }

        public String getStatusText() {
            return statusCode.toString();
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void close() {
        }

    }

    private static final class RecordingClientHttpRequestExecution implements ClientHttpRequestExecution {

        private HttpRequest request;

        private byte[] body;

        @Override
        public ClientHttpResponse execute(HttpRequest request, byte[] body) {
            this.request = request;
            this.body = body;
            return new SimpleClientHttpResponse(HttpStatus.CREATED);
        }

        private HttpRequest request() {
            return request;
        }

        private byte[] body() {
            return body;
        }

    }

    private static final class RecordingLoadBalancerClient implements LoadBalancerClient {

        private final ServiceInstance serviceInstance;

        private String serviceId;

        private RecordingLoadBalancerClient(ServiceInstance serviceInstance) {
            this.serviceInstance = serviceInstance;
        }

        @Override
        public ServiceInstance choose(String serviceId) {
            this.serviceId = serviceId;
            return serviceInstance;
        }

        @Override
        public <T> ServiceInstance choose(String serviceId, Request<T> request) {
            this.serviceId = serviceId;
            return serviceInstance;
        }

        @Override
        public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
            this.serviceId = serviceId;
            return execute(serviceId, serviceInstance, request);
        }

        @Override
        public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request)
                throws IOException {
            this.serviceId = serviceId;
            try {
                return request.apply(serviceInstance);
            }
            catch (IOException exception) {
                throw exception;
            }
            catch (Exception exception) {
                throw new IOException(exception);
            }
        }

        @Override
        public URI reconstructURI(ServiceInstance instance, URI original) {
            return LoadBalancerUriTools.reconstructURI(instance, original);
        }

        private String serviceId() {
            return serviceId;
        }

    }

    private static final class RecordingDiscoveryClient implements DiscoveryClient {

        private final int order;

        private final List<String> services;

        private final Map<String, List<ServiceInstance>> instances;

        private int probeCount;

        private RecordingDiscoveryClient(int order, List<String> services,
                Map<String, List<ServiceInstance>> instances) {
            this.order = order;
            this.services = services;
            this.instances = instances;
        }

        @Override
        public String description() {
            return "recording";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return instances.getOrDefault(serviceId, List.of());
        }

        @Override
        public List<String> getServices() {
            return services;
        }

        @Override
        public void probe() {
            probeCount++;
        }

        @Override
        public int getOrder() {
            return order;
        }

        private int probeCount() {
            return probeCount;
        }

    }

    private static final class RecordingCircuitBreakerFactory
            extends CircuitBreakerFactory<String, RecordingConfigBuilder> {

        private Function<String, String> defaultConfiguration = id -> "";

        @Override
        public CircuitBreaker create(String id) {
            return new RecordingCircuitBreaker();
        }

        @Override
        public void configureDefault(Function<String, String> defaultConfiguration) {
            this.defaultConfiguration = defaultConfiguration;
        }

        @Override
        protected RecordingConfigBuilder configBuilder(String id) {
            return new RecordingConfigBuilder(id);
        }

        private String configurationFor(String id) {
            return getConfigurations().get(id);
        }

        private String defaultConfigurationFor(String id) {
            return defaultConfiguration.apply(id);
        }

    }

    private static final class RecordingConfigBuilder implements ConfigBuilder<String> {

        private final String id;

        private String value;

        private RecordingConfigBuilder(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private void value(String value) {
            this.value = value;
        }

        @Override
        public String build() {
            return value;
        }

    }

    private static final class RecordingCircuitBreaker implements CircuitBreaker {

        @Override
        public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
            try {
                return toRun.get();
            }
            catch (Throwable throwable) {
                return fallback.apply(throwable);
            }
        }

    }

}
