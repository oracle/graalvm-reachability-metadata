/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry_instrumentation.opentelemetry_instrumentation_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.url.UrlAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class Opentelemetry_instrumentation_apiTest {
    private static final AttributeKey<String> HTTP_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> HTTP_METHOD_ORIGINAL =
            AttributeKey.stringKey("http.request.method_original");
    private static final AttributeKey<Long> HTTP_STATUS = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> HTTP_ROUTE = AttributeKey.stringKey("http.route");
    private static final AttributeKey<Long> HTTP_RESEND_COUNT = AttributeKey.longKey("http.request.resend_count");
    private static final AttributeKey<String> URL_FULL = AttributeKey.stringKey("url.full");
    private static final AttributeKey<String> URL_SCHEME = AttributeKey.stringKey("url.scheme");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    private static final AttributeKey<String> URL_QUERY = AttributeKey.stringKey("url.query");
    private static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    private static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");
    private static final AttributeKey<String> CLIENT_ADDRESS = AttributeKey.stringKey("client.address");
    private static final AttributeKey<Long> CLIENT_PORT = AttributeKey.longKey("client.port");
    private static final AttributeKey<String> NETWORK_TYPE = AttributeKey.stringKey("network.type");
    private static final AttributeKey<String> NETWORK_TRANSPORT = AttributeKey.stringKey("network.transport");
    private static final AttributeKey<String> NETWORK_PROTOCOL_NAME = AttributeKey.stringKey("network.protocol.name");
    private static final AttributeKey<String> NETWORK_PROTOCOL_VERSION =
            AttributeKey.stringKey("network.protocol.version");
    private static final AttributeKey<String> NETWORK_LOCAL_ADDRESS = AttributeKey.stringKey("network.local.address");
    private static final AttributeKey<Long> NETWORK_LOCAL_PORT = AttributeKey.longKey("network.local.port");
    private static final AttributeKey<String> NETWORK_PEER_ADDRESS = AttributeKey.stringKey("network.peer.address");
    private static final AttributeKey<Long> NETWORK_PEER_PORT = AttributeKey.longKey("network.peer.port");
    private static final ContextKey<String> CUSTOM_CONTEXT_KEY = ContextKey.named("customized-context");
    private static final ContextKey<String> PROPAGATED_CONTEXT_KEY = ContextKey.named("propagated-context");
    private static final String PROPAGATION_HEADER = "x-propagated-context";

    @Test
    void clientHttpExtractorCapturesMethodUrlHeadersResponseAndResendCount() {
        ClientRequest request = new ClientRequest(
                "GET",
                "https://api.example.test:8443/search?q=java",
                "api.example.test",
                8443,
                Map.of("authorization", List.of("Bearer testing")),
                "/unused");
        ClientResponse response = new ClientResponse(503, Map.of("retry-after", List.of("7")));
        AttributesExtractor<ClientRequest, ClientResponse> extractor = HttpClientAttributesExtractor
                .builder(new TestHttpClientAttributesGetter())
                .setCapturedRequestHeaders(List.of("Authorization"))
                .setCapturedResponseHeaders(List.of("Retry-After"))
                .build();
        Context context = HttpClientRequestResendCount.initialize(Context.root());
        assertThat(HttpClientRequestResendCount.get(context)).isEqualTo(0);

        AttributesBuilder builder = Attributes.builder();
        extractor.onStart(builder, context, request);
        extractor.onEnd(builder, context, request, response, null);
        Attributes attributes = builder.build();

        assertThat(HttpClientRequestResendCount.get(context)).isEqualTo(1);
        assertThat(attributes.get(HTTP_METHOD)).isEqualTo("GET");
        assertThat(attributes.get(URL_FULL)).isEqualTo("https://api.example.test:8443/search?q=java");
        assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("api.example.test");
        assertThat(attributes.get(SERVER_PORT)).isEqualTo(8443L);
        assertThat(attributes.get(HTTP_STATUS)).isEqualTo(503L);
        assertThat(attributes.get(AttributeKey.stringArrayKey("http.request.header.authorization")))
                .containsExactly("Bearer testing");
        assertThat(attributes.get(AttributeKey.stringArrayKey("http.response.header.retry-after")))
                .containsExactly("7");
    }

    @Test
    void clientHttpExtractorNormalizesUnknownMethodAndRecordsResendCountOnRetry() {
        ClientRequest request = new ClientRequest(
                "BREW",
                "https://coffee.example.test/pot",
                "coffee.example.test",
                443,
                Collections.emptyMap(),
                "/unused");
        AttributesExtractor<ClientRequest, ClientResponse> extractor = HttpClientAttributesExtractor
                .builder(new TestHttpClientAttributesGetter())
                .setKnownMethods(Set.of("GET", "POST"))
                .build();
        Context context = HttpClientRequestResendCount.initialize(Context.root());
        extractor.onStart(Attributes.builder(), context, request);

        AttributesBuilder builder = Attributes.builder();
        extractor.onStart(builder, context, request);
        Attributes attributes = builder.build();

        assertThat(attributes.get(HTTP_METHOD)).isEqualTo("_OTHER");
        assertThat(attributes.get(HTTP_METHOD_ORIGINAL)).isEqualTo("BREW");
        assertThat(attributes.get(HTTP_RESEND_COUNT)).isEqualTo(1L);
        assertThat(HttpClientRequestResendCount.get(context)).isEqualTo(2);
    }

    @Test
    void serverHttpExtractorCapturesRouteUrlClientAndResponseAttributes() {
        ServerRequest request = new ServerRequest(
                "POST",
                "https",
                "/orders/123",
                "includeDetails=true",
                "/orders/{id}",
                "203.0.113.10",
                51515,
                Map.of("x-request-id", List.of("request-1")));
        ServerResponse response = new ServerResponse(201, Map.of("content-type", List.of("application/json")));
        AttributesExtractor<ServerRequest, ServerResponse> extractor = HttpServerAttributesExtractor
                .builder(new TestHttpServerAttributesGetter())
                .setCapturedRequestHeaders(List.of("X-Request-Id"))
                .setCapturedResponseHeaders(List.of("Content-Type"))
                .build();

        AttributesBuilder builder = Attributes.builder();
        extractor.onStart(builder, Context.root(), request);
        extractor.onEnd(builder, Context.root(), request, response, null);
        Attributes attributes = builder.build();

        assertThat(attributes.get(HTTP_METHOD)).isEqualTo("POST");
        assertThat(attributes.get(URL_SCHEME)).isEqualTo("https");
        assertThat(attributes.get(URL_PATH)).isEqualTo("/orders/123");
        assertThat(attributes.get(URL_QUERY)).isEqualTo("includeDetails=true");
        assertThat(attributes.get(HTTP_ROUTE)).isEqualTo("/orders/{id}");
        assertThat(attributes.get(CLIENT_ADDRESS)).isEqualTo("203.0.113.10");
        assertThat(attributes.get(HTTP_STATUS)).isEqualTo(201L);
        assertThat(attributes.get(AttributeKey.stringArrayKey("http.request.header.x-request-id")))
                .containsExactly("request-1");
        assertThat(attributes.get(AttributeKey.stringArrayKey("http.response.header.content-type")))
                .containsExactly("application/json");
    }

    @Test
    void spanNameExtractorsUseHttpMethodsAndServerRoutes() {
        ClientRequest clientRequest = new ClientRequest(
                "PATCH",
                "https://api.example.test/widgets/7",
                "api.example.test",
                443,
                Collections.emptyMap(),
                "/unused");
        ServerRequest serverRequest = new ServerRequest(
                "DELETE",
                "https",
                "/widgets/7",
                null,
                "/widgets/{id}",
                "198.51.100.20",
                41414,
                Collections.emptyMap());

        SpanNameExtractor<ClientRequest> clientNameExtractor = HttpSpanNameExtractor
                .builder(new TestHttpClientAttributesGetter())
                .setKnownMethods(Set.of("PATCH"))
                .build();
        SpanNameExtractor<ServerRequest> serverNameExtractor = HttpSpanNameExtractor
                .builder(new TestHttpServerAttributesGetter())
                .build();

        assertThat(clientNameExtractor.extract(clientRequest)).isEqualTo("PATCH");
        assertThat(serverNameExtractor.extract(serverRequest)).isEqualTo("DELETE /widgets/{id}");
    }

    @Test
    void httpSpanStatusExtractorsApplyClientAndServerErrorRules() {
        ClientRequest clientRequest = new ClientRequest(
                "GET",
                "https://api.example.test/missing",
                "api.example.test",
                443,
                Collections.emptyMap(),
                "/missing");
        ServerRequest serverRequest = new ServerRequest(
                "GET",
                "https",
                "/missing",
                null,
                null,
                "198.51.100.30",
                42525,
                Collections.emptyMap());
        SpanStatusExtractor<ClientRequest, ClientResponse> clientStatusExtractor =
                HttpSpanStatusExtractor.create(new TestHttpClientAttributesGetter());
        SpanStatusExtractor<ServerRequest, ServerResponse> serverStatusExtractor =
                HttpSpanStatusExtractor.create(new TestHttpServerAttributesGetter());

        RecordingSpanStatusBuilder clientNotFoundStatus = new RecordingSpanStatusBuilder();
        clientStatusExtractor.extract(
                clientNotFoundStatus, clientRequest, new ClientResponse(404, Collections.emptyMap()), null);

        RecordingSpanStatusBuilder serverNotFoundStatus = new RecordingSpanStatusBuilder();
        serverStatusExtractor.extract(
                serverNotFoundStatus, serverRequest, new ServerResponse(404, Collections.emptyMap()), null);

        RecordingSpanStatusBuilder serverFailureStatus = new RecordingSpanStatusBuilder();
        serverStatusExtractor.extract(
                serverFailureStatus, serverRequest, new ServerResponse(500, Collections.emptyMap()), null);

        RecordingSpanStatusBuilder clientTransportFailureStatus = new RecordingSpanStatusBuilder();
        clientStatusExtractor.extract(
                clientTransportFailureStatus,
                clientRequest,
                null,
                new IllegalStateException("connection closed"));

        assertThat(clientNotFoundStatus.statusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(serverNotFoundStatus.statusCode()).isNull();
        assertThat(serverFailureStatus.statusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(clientTransportFailureStatus.statusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void standaloneNetworkAndUrlExtractorsPopulateExpectedAttributes() {
        ServerRequest request = new ServerRequest(
                "GET",
                "HTTPS",
                "/health",
                "verbose=true",
                null,
                "192.0.2.5",
                52525,
                Collections.emptyMap());
        ServerResponse response = new ServerResponse(200, Collections.emptyMap());
        TestNetworkGetter networkGetter = new TestNetworkGetter();
        AttributesBuilder builder = Attributes.builder();

        ClientAttributesExtractor.<ServerRequest, ServerResponse>create(networkGetter)
                .onStart(builder, Context.root(), request);
        ServerAttributesExtractor.<ServerRequest, ServerResponse>create(networkGetter)
                .onStart(builder, Context.root(), request);
        UrlAttributesExtractor.<ServerRequest, ServerResponse>create(new TestHttpServerAttributesGetter())
                .onStart(builder, Context.root(), request);
        NetworkAttributesExtractor.create(networkGetter)
                .onEnd(builder, Context.root(), request, response, null);
        Attributes attributes = builder.build();

        assertThat(attributes.get(CLIENT_ADDRESS)).isEqualTo("192.0.2.5");
        assertThat(attributes.get(CLIENT_PORT)).isEqualTo(52525L);
        assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("service.example.test");
        assertThat(attributes.get(SERVER_PORT)).isEqualTo(9443L);
        assertThat(attributes.get(URL_SCHEME)).isEqualTo("HTTPS");
        assertThat(attributes.get(URL_PATH)).isEqualTo("/health");
        assertThat(attributes.get(URL_QUERY)).isEqualTo("verbose=true");
        assertThat(attributes.get(NETWORK_TYPE)).isEqualTo("ipv4");
        assertThat(attributes.get(NETWORK_TRANSPORT)).isEqualTo("tcp");
        assertThat(attributes.get(NETWORK_PROTOCOL_NAME)).isEqualTo("http");
        assertThat(attributes.get(NETWORK_PROTOCOL_VERSION)).isEqualTo("1.1");
        assertThat(attributes.get(NETWORK_LOCAL_ADDRESS)).isEqualTo("10.0.0.5");
        assertThat(attributes.get(NETWORK_LOCAL_PORT)).isEqualTo(9443L);
        assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isEqualTo("192.0.2.5");
        assertThat(attributes.get(NETWORK_PEER_PORT)).isEqualTo(52525L);
    }

    @Test
    void instrumenterInvokesExtractorsCustomizersAndOperationListeners() {
        AtomicInteger onStartCount = new AtomicInteger();
        AtomicInteger onEndCount = new AtomicInteger();
        AtomicReference<Attributes> startAttributes = new AtomicReference<>();
        AtomicReference<Attributes> endAttributes = new AtomicReference<>();
        ClientRequest request = new ClientRequest(
                "GET",
                "https://example.test/resource",
                "example.test",
                443,
                Collections.emptyMap(),
                "/resource");
        ClientResponse response = new ClientResponse(204, Collections.emptyMap());
        Instrumenter<ClientRequest, ClientResponse> instrumenter = Instrumenter
                .<ClientRequest, ClientResponse>builder(
                        OpenTelemetry.noop(), "test-library", ignored -> "client operation")
                .addAttributesExtractor(AttributesExtractor.constant(AttributeKey.stringKey("test.start"), "created"))
                .addAttributesExtractor(new ResponseCodeExtractor())
                .addContextCustomizer((context, ignored, attributes) -> context.with(
                        CUSTOM_CONTEXT_KEY,
                        attributes.get(AttributeKey.stringKey("test.start"))))
                .addOperationListener(new OperationListener() {
                    @Override
                    public Context onStart(Context context, Attributes attributes, long startNanos) {
                        onStartCount.incrementAndGet();
                        startAttributes.set(attributes);
                        assertThat(context.get(CUSTOM_CONTEXT_KEY)).isEqualTo("created");
                        return context;
                    }

                    @Override
                    public void onEnd(Context context, Attributes attributes, long endNanos) {
                        onEndCount.incrementAndGet();
                        endAttributes.set(attributes);
                        assertThat(context.get(CUSTOM_CONTEXT_KEY)).isEqualTo("created");
                    }
                })
                .buildInstrumenter(SpanKindExtractor.alwaysClient());

        assertThat(instrumenter.shouldStart(Context.root(), request)).isTrue();
        Context started = instrumenter.start(Context.root(), request);
        instrumenter.end(started, request, response, null);

        assertThat(started.get(CUSTOM_CONTEXT_KEY)).isEqualTo("created");
        assertThat(onStartCount).hasValue(1);
        assertThat(onEndCount).hasValue(1);
        assertThat(startAttributes.get().get(AttributeKey.stringKey("test.start"))).isEqualTo("created");
        assertThat(endAttributes.get().get(AttributeKey.longKey("test.status"))).isEqualTo(204L);
    }

    @Test
    void disabledInstrumenterDoesNotStart() {
        ClientRequest request = new ClientRequest(
                "GET",
                "https://example.test/disabled",
                "example.test",
                443,
                Collections.emptyMap(),
                "/disabled");
        Instrumenter<ClientRequest, ClientResponse> instrumenter = Instrumenter
                .<ClientRequest, ClientResponse>builder(OpenTelemetry.noop(), "disabled-library", ignored -> "disabled")
                .setEnabled(false)
                .buildInstrumenter();

        assertThat(instrumenter.shouldStart(Context.root(), request)).isFalse();
    }

    @Test
    void propagatingInstrumentersInjectAndExtractCarrierValues() {
        OpenTelemetry openTelemetry = OpenTelemetry.propagating(
                ContextPropagators.create(new TestTextMapPropagator()));
        ClientRequest clientRequest = new ClientRequest(
                "POST",
                "https://downstream.example.test/jobs",
                "downstream.example.test",
                443,
                new HashMap<>(),
                "/jobs");
        Instrumenter<ClientRequest, ClientResponse> clientInstrumenter = Instrumenter
                .<ClientRequest, ClientResponse>builder(openTelemetry, "propagating-client", ignored -> "client send")
                .buildClientInstrumenter(new HeaderTextMapSetter());

        Context clientParent = Context.root().with(PROPAGATED_CONTEXT_KEY, "client-parent");
        Context clientContext = clientInstrumenter.start(clientParent, clientRequest);
        clientInstrumenter.end(clientContext, clientRequest, new ClientResponse(202, Collections.emptyMap()), null);

        assertThat(clientRequest.headers().get(PROPAGATION_HEADER)).containsExactly("client-parent");

        ServerRequest serverRequest = new ServerRequest(
                "GET",
                "https",
                "/callbacks/ready",
                null,
                "/callbacks/{state}",
                "203.0.113.25",
                49152,
                Map.of(PROPAGATION_HEADER, List.of("server-parent")));
        Instrumenter<ServerRequest, ServerResponse> serverInstrumenter = Instrumenter
                .<ServerRequest, ServerResponse>builder(
                        openTelemetry, "propagating-server", ignored -> "server receive")
                .buildServerInstrumenter(new HeaderTextMapGetter());

        Context serverContext = serverInstrumenter.start(Context.root(), serverRequest);
        serverInstrumenter.end(serverContext, serverRequest, new ServerResponse(204, Collections.emptyMap()), null);

        assertThat(serverContext.get(PROPAGATED_CONTEXT_KEY)).isEqualTo("server-parent");
    }

    @Test
    void virtualFieldStoresAttachmentsByObjectIdentityAndFieldType() {
        VirtualField<CarryObject, Attachment> attachmentField = VirtualField.find(CarryObject.class, Attachment.class);
        VirtualField<CarryObject, String> stringField = VirtualField.find(CarryObject.class, String.class);
        CarryObject first = new CarryObject("first");
        CarryObject second = new CarryObject("second");
        Attachment attachment = new Attachment("metadata");

        attachmentField.set(first, attachment);
        stringField.set(first, "side-channel");
        attachmentField.set(second, new Attachment("other"));

        assertThat(attachmentField.get(first)).isSameAs(attachment);
        assertThat(stringField.get(first)).isEqualTo("side-channel");
        assertThat(attachmentField.get(second).value()).isEqualTo("other");
        assertThat(stringField.get(second)).isNull();
    }

    private record ClientRequest(
            String method,
            String url,
            String serverAddress,
            int serverPort,
            Map<String, List<String>> headers,
            String path) {
    }

    private record ClientResponse(int statusCode, Map<String, List<String>> headers) {
    }

    private record ServerRequest(
            String method,
            String scheme,
            String path,
            String query,
            String route,
            String clientAddress,
            int clientPort,
            Map<String, List<String>> headers) {
    }

    private record ServerResponse(int statusCode, Map<String, List<String>> headers) {
    }

    private record CarryObject(String name) {
    }

    private record Attachment(String value) {
    }

    private static final class RecordingSpanStatusBuilder implements SpanStatusBuilder {
        private StatusCode statusCode;

        @Override
        public SpanStatusBuilder setStatus(StatusCode statusCode, String description) {
            this.statusCode = statusCode;
            return this;
        }

        private StatusCode statusCode() {
            return statusCode;
        }
    }

    private static final class TestTextMapPropagator implements TextMapPropagator {
        @Override
        public Collection<String> fields() {
            return List.of(PROPAGATION_HEADER);
        }

        @Override
        public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
            String value = context.get(PROPAGATED_CONTEXT_KEY);
            if (value != null) {
                setter.set(carrier, PROPAGATION_HEADER, value);
            }
        }

        @Override
        public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
            String value = getter.get(carrier, PROPAGATION_HEADER);
            return value == null ? context : context.with(PROPAGATED_CONTEXT_KEY, value);
        }
    }

    private static final class HeaderTextMapSetter implements TextMapSetter<ClientRequest> {
        @Override
        public void set(ClientRequest carrier, String key, String value) {
            carrier.headers().put(key, List.of(value));
        }
    }

    private static final class HeaderTextMapGetter implements TextMapGetter<ServerRequest> {
        @Override
        public Iterable<String> keys(ServerRequest carrier) {
            return carrier.headers().keySet();
        }

        @Override
        public String get(ServerRequest carrier, String key) {
            List<String> values = carrier.headers().getOrDefault(key, Collections.emptyList());
            return values.isEmpty() ? null : values.get(0);
        }
    }

    private static final class TestHttpClientAttributesGetter
            implements HttpClientAttributesGetter<ClientRequest, ClientResponse> {
        @Override
        public String getUrlFull(ClientRequest request) {
            return request.url();
        }

        @Override
        public String getServerAddress(ClientRequest request) {
            return request.serverAddress();
        }

        @Override
        public Integer getServerPort(ClientRequest request) {
            return request.serverPort();
        }

        @Override
        public String getHttpRequestMethod(ClientRequest request) {
            return request.method();
        }

        @Override
        public List<String> getHttpRequestHeader(ClientRequest request, String name) {
            return request.headers().getOrDefault(name, Collections.emptyList());
        }

        @Override
        public Integer getHttpResponseStatusCode(ClientRequest request, ClientResponse response, Throwable error) {
            return response == null ? null : response.statusCode();
        }

        @Override
        public List<String> getHttpResponseHeader(ClientRequest request, ClientResponse response, String name) {
            if (response == null) {
                return Collections.emptyList();
            }
            return response.headers().getOrDefault(name, Collections.emptyList());
        }
    }

    private static class TestHttpServerAttributesGetter
            implements HttpServerAttributesGetter<ServerRequest, ServerResponse> {
        @Override
        public String getUrlScheme(ServerRequest request) {
            return request.scheme();
        }

        @Override
        public String getUrlPath(ServerRequest request) {
            return request.path();
        }

        @Override
        public String getUrlQuery(ServerRequest request) {
            return request.query();
        }

        @Override
        public String getHttpRoute(ServerRequest request) {
            return request.route();
        }

        @Override
        public String getClientAddress(ServerRequest request) {
            return request.clientAddress();
        }

        @Override
        public Integer getClientPort(ServerRequest request) {
            return request.clientPort();
        }

        @Override
        public String getHttpRequestMethod(ServerRequest request) {
            return request.method();
        }

        @Override
        public List<String> getHttpRequestHeader(ServerRequest request, String name) {
            return request.headers().getOrDefault(name, Collections.emptyList());
        }

        @Override
        public Integer getHttpResponseStatusCode(ServerRequest request, ServerResponse response, Throwable error) {
            return response == null ? null : response.statusCode();
        }

        @Override
        public List<String> getHttpResponseHeader(ServerRequest request, ServerResponse response, String name) {
            if (response == null) {
                return Collections.emptyList();
            }
            return response.headers().getOrDefault(name, Collections.emptyList());
        }
    }

    private static final class TestNetworkGetter extends TestHttpServerAttributesGetter
            implements ServerAttributesGetter<ServerRequest> {
        @Override
        public String getServerAddress(ServerRequest request) {
            return "service.example.test";
        }

        @Override
        public Integer getServerPort(ServerRequest request) {
            return 9443;
        }

        @Override
        public String getNetworkType(ServerRequest request, ServerResponse response) {
            return "IPV4";
        }

        @Override
        public String getNetworkTransport(ServerRequest request, ServerResponse response) {
            return "TCP";
        }

        @Override
        public String getNetworkProtocolName(ServerRequest request, ServerResponse response) {
            return "HTTP";
        }

        @Override
        public String getNetworkProtocolVersion(ServerRequest request, ServerResponse response) {
            return "1.1";
        }

        @Override
        public String getNetworkLocalAddress(ServerRequest request, ServerResponse response) {
            return "10.0.0.5";
        }

        @Override
        public Integer getNetworkLocalPort(ServerRequest request, ServerResponse response) {
            return 9443;
        }

        @Override
        public String getNetworkPeerAddress(ServerRequest request, ServerResponse response) {
            return request.clientAddress();
        }

        @Override
        public Integer getNetworkPeerPort(ServerRequest request, ServerResponse response) {
            return request.clientPort();
        }
    }

    private static final class ResponseCodeExtractor implements AttributesExtractor<ClientRequest, ClientResponse> {
        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, ClientRequest request) {
            attributes.put(AttributeKey.stringKey("test.path"), request.path());
        }

        @Override
        public void onEnd(
                AttributesBuilder attributes,
                Context context,
                ClientRequest request,
                ClientResponse response,
                Throwable error) {
            attributes.put(AttributeKey.longKey("test.status"), response.statusCode());
        }
    }
}
