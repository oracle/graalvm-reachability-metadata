/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_websocket.jakarta_websocket_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Decoder;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerApplicationConfig;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class Jakarta_websocket_apiTest {
    @Test
    void serverEndpointConfigBuilderCreatesImmutableConfigurationWithCustomConfigurator() {
        RecordingConfigurator configurator = new RecordingConfigurator();
        Extension extension = new SimpleExtension("permessage-deflate", List.of(new SimpleParameter("level", "9")));
        List<String> subprotocols = new ArrayList<>(List.of("json", "cbor"));
        List<Extension> extensions = new ArrayList<>(List.of(extension));
        List<Class<? extends Encoder>> encoders = new ArrayList<>(List.of(IntegerTextEncoder.class));
        List<Class<? extends Decoder>> decoders = new ArrayList<>(List.of(IntegerTextDecoder.class));

        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "/rooms/{room}")
                .subprotocols(subprotocols)
                .extensions(extensions)
                .encoders(encoders)
                .decoders(decoders)
                .configurator(configurator)
                .build();

        assertThat(config.getEndpointClass()).isSameAs(ProgrammaticEndpoint.class);
        assertThat(config.getPath()).isEqualTo("/rooms/{room}");
        assertThat(config.getSubprotocols()).containsExactly("json", "cbor");
        assertThat(config.getExtensions()).containsExactly(extension);
        assertThat(config.getEncoders()).containsExactly(IntegerTextEncoder.class);
        assertThat(config.getDecoders()).containsExactly(IntegerTextDecoder.class);
        assertThat(config.getConfigurator()).isSameAs(configurator);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> config.getSubprotocols().add("xml"));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> config.getExtensions().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> config.getEncoders().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> config.getDecoders().clear());

        config.getUserProperties().put("tenant", "blue");
        assertThat(config.getUserProperties()).containsEntry("tenant", "blue");
    }

    @Test
    void builderValidatesEndpointClassAndPathAndTreatsNullListsAsEmpty() {
        RecordingConfigurator configurator = new RecordingConfigurator();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ServerEndpointConfig.Builder.create(null, "/valid"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, null));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "relative"));

        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "/empty")
                .subprotocols(null)
                .extensions(null)
                .encoders(null)
                .decoders(null)
                .configurator(configurator)
                .build();

        assertThat(config.getSubprotocols()).isEmpty();
        assertThat(config.getExtensions()).isEmpty();
        assertThat(config.getEncoders()).isEmpty();
        assertThat(config.getDecoders()).isEmpty();
        assertThat(config.getConfigurator()).isSameAs(configurator);
    }

    @Test
    void configuratorNegotiatesSubprotocolExtensionsOriginAndEndpointInstances() throws Exception {
        RecordingConfigurator configurator = new RecordingConfigurator();
        Extension deflate = new SimpleExtension(
                "permessage-deflate", List.of(new SimpleParameter("server_max_window_bits", "12")));
        Extension unknown = new SimpleExtension("x-unknown", List.of());

        String subprotocol = configurator.getNegotiatedSubprotocol(List.of("json", "cbor"), List.of("xml", "cbor"));
        List<Extension> extensions = configurator.getNegotiatedExtensions(List.of(deflate), List.of(unknown, deflate));
        ProgrammaticEndpoint endpoint = configurator.getEndpointInstance(ProgrammaticEndpoint.class);

        assertThat(subprotocol).isEqualTo("cbor");
        assertThat(configurator.getNegotiatedSubprotocol(List.of("json"), List.of("xml"))).isEmpty();
        assertThat(extensions).containsExactly(deflate);
        assertThat(configurator.checkOrigin("https://trusted.example/app")).isTrue();
        assertThat(configurator.checkOrigin("https://evil.example/app")).isFalse();
        assertThat(endpoint).isNotNull();
        assertThatExceptionOfType(InstantiationException.class)
                .isThrownBy(() -> configurator.getEndpointInstance(UnsupportedEndpoint.class));
    }

    @Test
    void configuratorModifyHandshakeCanObserveRequestAndMutateConfigAndResponse() {
        RecordingConfigurator configurator = new RecordingConfigurator();
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "/rooms/{room}")
                .configurator(configurator)
                .build();
        SimpleHandshakeRequest request = new SimpleHandshakeRequest(
                URI.create("wss://example.test/rooms/alpha?debug=true"),
                Map.of(
                        HandshakeRequest.SEC_WEBSOCKET_KEY, List.of("client-key"),
                        HandshakeRequest.SEC_WEBSOCKET_PROTOCOL, List.of("json")),
                Map.of("debug", List.of("true")),
                "debug=true",
                new SimplePrincipal("alice"),
                Set.of("admin"),
                "HTTP-SESSION-1");
        SimpleHandshakeResponse response = new SimpleHandshakeResponse(new HashMap<>());

        configurator.modifyHandshake(config, request, response);

        assertThat(config.getUserProperties())
                .containsEntry("requestUri", URI.create("wss://example.test/rooms/alpha?debug=true"))
                .containsEntry("principalName", "alice")
                .containsEntry("admin", true)
                .containsEntry("httpSession", "HTTP-SESSION-1");
        assertThat(response.getHeaders()).containsEntry("X-Selected-Protocol", List.of("json"));
        assertThat(response.getHeaders())
                .containsEntry(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, List.of("accepted-client-key"));
    }

    @Test
    void handshakeRequestExposesHeadersParametersPrincipalRolesAndConstants() {
        URI requestUri = URI.create("wss://example.test/chat?room=blue&room=green");
        SimplePrincipal principal = new SimplePrincipal("carol");
        SimpleHandshakeRequest request = new SimpleHandshakeRequest(
                requestUri,
                Map.of(HandshakeRequest.SEC_WEBSOCKET_VERSION, List.of("13")),
                Map.of("room", List.of("blue", "green")),
                "room=blue&room=green",
                principal,
                Set.of("user"),
                123L);

        assertThat(HandshakeRequest.SEC_WEBSOCKET_KEY).isEqualTo("Sec-WebSocket-Key");
        assertThat(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL).isEqualTo("Sec-WebSocket-Protocol");
        assertThat(HandshakeRequest.SEC_WEBSOCKET_VERSION).isEqualTo("Sec-WebSocket-Version");
        assertThat(HandshakeRequest.SEC_WEBSOCKET_EXTENSIONS).isEqualTo("Sec-WebSocket-Extensions");
        assertThat(request.getRequestURI()).isSameAs(requestUri);
        assertThat(request.getHeaders()).containsEntry(HandshakeRequest.SEC_WEBSOCKET_VERSION, List.of("13"));
        assertThat(request.getParameterMap()).containsEntry("room", List.of("blue", "green"));
        assertThat(request.getQueryString()).isEqualTo("room=blue&room=green");
        assertThat(request.getUserPrincipal()).isSameAs(principal);
        assertThat(request.isUserInRole("user")).isTrue();
        assertThat(request.isUserInRole("admin")).isFalse();
        assertThat(request.getHttpSession()).isEqualTo(123L);
    }

    @Test
    void serverApplicationConfigSelectsProgrammaticConfigsAndAnnotatedEndpoints() {
        RecordingServerApplicationConfig applicationConfig = new RecordingServerApplicationConfig();
        Set<Class<? extends Endpoint>> endpointClasses = new LinkedHashSet<>(
                List.of(ProgrammaticEndpoint.class, UnsupportedEndpoint.class));
        Set<Class<?>> annotatedCandidates = new LinkedHashSet<>(
                List.of(AnnotatedChatEndpoint.class, PlainEndpoint.class));

        Set<ServerEndpointConfig> programmaticConfigs = applicationConfig.getEndpointConfigs(endpointClasses);
        Set<Class<?>> annotatedEndpoints = applicationConfig.getAnnotatedEndpointClasses(annotatedCandidates);

        assertThat(programmaticConfigs).hasSize(1);
        ServerEndpointConfig config = programmaticConfigs.iterator().next();
        assertThat(config.getEndpointClass()).isSameAs(ProgrammaticEndpoint.class);
        assertThat(config.getPath()).isEqualTo("/programmatic/{id}");
        assertThat(config.getSubprotocols()).containsExactly("json");
        assertThat(annotatedEndpoints).containsExactly(AnnotatedChatEndpoint.class);
    }

    @Test
    void serverContainerRecordsEndpointRegistrationUpgradeAndContainerDefaults() throws Exception {
        RecordingServerContainer container = new RecordingServerContainer(
                Set.of(new SimpleExtension("permessage-deflate", List.of())));
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "/upgrade/{id}")
                .configurator(new RecordingConfigurator())
                .build();

        container.setAsyncSendTimeout(1_500L);
        container.setDefaultMaxSessionIdleTimeout(45_000L);
        container.setDefaultMaxBinaryMessageBufferSize(8_192);
        container.setDefaultMaxTextMessageBufferSize(4_096);
        container.addEndpoint(AnnotatedChatEndpoint.class);
        container.addEndpoint(config);
        container.upgradeHttpToWebSocket("request", "response", config, Map.of("id", "42"));

        assertThat(container.getDefaultAsyncSendTimeout()).isEqualTo(1_500L);
        assertThat(container.getDefaultMaxSessionIdleTimeout()).isEqualTo(45_000L);
        assertThat(container.getDefaultMaxBinaryMessageBufferSize()).isEqualTo(8_192);
        assertThat(container.getDefaultMaxTextMessageBufferSize()).isEqualTo(4_096);
        assertThat(container.getInstalledExtensions())
                .extracting(Extension::getName)
                .containsExactly("permessage-deflate");
        assertThat(container.annotatedEndpointClasses()).containsExactly(AnnotatedChatEndpoint.class);
        assertThat(container.programmaticEndpointConfigs()).containsExactly(config);
        assertThat(container.upgradeRequest()).isEqualTo("request");
        assertThat(container.upgradeResponse()).isEqualTo("response");
        assertThat(container.upgradeConfig()).isSameAs(config);
        assertThat(container.upgradePathParameters()).containsEntry("id", "42");
    }

    @Test
    void annotatedEndpointUsesServerAnnotationsAndPathParametersInEndpointMethods() {
        AnnotatedChatEndpoint endpoint = new AnnotatedChatEndpoint();
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");

        endpoint.open("alpha");
        String reply = endpoint.onMessage("hello", "alpha");
        endpoint.close(closeReason, "alpha");

        assertThat(reply).isEqualTo("alpha:hello");
        assertThat(endpoint.events()).containsExactly("open:alpha", "message:alpha:hello", "close:alpha:1000");
    }

    @ServerEndpoint(
            value = "/annotated/{room}",
            subprotocols = {"json", "cbor"},
            encoders = {IntegerTextEncoder.class},
            decoders = {IntegerTextDecoder.class},
            configurator = RecordingConfigurator.class)
    public static class AnnotatedChatEndpoint {
        private final List<String> events = new ArrayList<>();

        @OnOpen
        public void open(@PathParam("room") String room) {
            events.add("open:" + room);
        }

        @OnMessage
        public String onMessage(String message, @PathParam("room") String room) {
            events.add("message:" + room + ":" + message);
            return room + ":" + message;
        }

        @OnClose
        public void close(CloseReason reason, @PathParam("room") String room) {
            events.add("close:" + room + ":" + reason.getCloseCode().getCode());
        }

        List<String> events() {
            return events;
        }
    }

    public static class ProgrammaticEndpoint extends Endpoint {
        private Session session;
        private EndpointConfig config;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            this.config = config;
        }

        Session session() {
            return session;
        }

        EndpointConfig config() {
            return config;
        }
    }

    public static class UnsupportedEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            throw new IllegalStateException("not used");
        }
    }

    public static class PlainEndpoint {
    }

    public static class IntegerTextEncoder implements Encoder.Text<Integer> {
        @Override
        public String encode(Integer value) throws EncodeException {
            return String.valueOf(value);
        }
    }

    public static class IntegerTextDecoder implements Decoder.Text<Integer> {
        @Override
        public Integer decode(String value) {
            return Integer.valueOf(value);
        }

        @Override
        public boolean willDecode(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                if (!Character.isDigit(value.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class RecordingConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
            for (String requestedProtocol : requested) {
                if (supported.contains(requestedProtocol)) {
                    return requestedProtocol;
                }
            }
            return "";
        }

        @Override
        public List<Extension> getNegotiatedExtensions(List<Extension> installed, List<Extension> requested) {
            List<Extension> negotiated = new ArrayList<>();
            for (Extension requestedExtension : requested) {
                for (Extension installedExtension : installed) {
                    if (installedExtension.getName().equals(requestedExtension.getName())) {
                        negotiated.add(installedExtension);
                    }
                }
            }
            return negotiated;
        }

        @Override
        public boolean checkOrigin(String originHeaderValue) {
            return originHeaderValue != null && originHeaderValue.startsWith("https://trusted.example");
        }

        @Override
        public void modifyHandshake(
                ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
            config.getUserProperties().put("requestUri", request.getRequestURI());
            config.getUserProperties().put("principalName", request.getUserPrincipal().getName());
            config.getUserProperties().put("admin", request.isUserInRole("admin"));
            config.getUserProperties().put("httpSession", request.getHttpSession());

            String protocol = request.getHeaders().get(HandshakeRequest.SEC_WEBSOCKET_PROTOCOL).get(0);
            String key = request.getHeaders().get(HandshakeRequest.SEC_WEBSOCKET_KEY).get(0);
            response.getHeaders().put("X-Selected-Protocol", List.of(protocol));
            response.getHeaders().put(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, List.of("accepted-" + key));
        }

        @Override
        public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            if (endpointClass.equals(ProgrammaticEndpoint.class)) {
                return endpointClass.cast(new ProgrammaticEndpoint());
            }
            throw new InstantiationException("Unsupported endpoint: " + endpointClass.getName());
        }
    }

    private static final class RecordingServerApplicationConfig implements ServerApplicationConfig {
        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
            Set<ServerEndpointConfig> configs = new LinkedHashSet<>();
            if (endpointClasses.contains(ProgrammaticEndpoint.class)) {
                configs.add(ServerEndpointConfig.Builder.create(ProgrammaticEndpoint.class, "/programmatic/{id}")
                        .subprotocols(List.of("json"))
                        .configurator(new RecordingConfigurator())
                        .build());
            }
            return configs;
        }

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            Set<Class<?>> endpoints = new LinkedHashSet<>();
            for (Class<?> candidate : scanned) {
                if (candidate.equals(AnnotatedChatEndpoint.class)) {
                    endpoints.add(candidate);
                }
            }
            return endpoints;
        }
    }

    private static final class RecordingServerContainer implements ServerContainer {
        private final Set<Extension> installedExtensions;
        private final List<Class<?>> annotatedEndpointClasses = new ArrayList<>();
        private final List<ServerEndpointConfig> programmaticEndpointConfigs = new ArrayList<>();
        private long asyncSendTimeout;
        private long idleTimeout;
        private int binaryBufferSize;
        private int textBufferSize;
        private Object upgradeRequest;
        private Object upgradeResponse;
        private ServerEndpointConfig upgradeConfig;
        private Map<String, String> upgradePathParameters = new HashMap<>();

        private RecordingServerContainer(Set<Extension> installedExtensions) {
            this.installedExtensions = installedExtensions;
        }

        @Override
        public void addEndpoint(Class<?> endpointClass) {
            annotatedEndpointClasses.add(endpointClass);
        }

        @Override
        public void addEndpoint(ServerEndpointConfig serverConfig) {
            programmaticEndpointConfigs.add(serverConfig);
        }

        @Override
        public void upgradeHttpToWebSocket(
                Object httpServletRequest,
                Object httpServletResponse,
                ServerEndpointConfig serverEndpointConfig,
                Map<String, String> pathParameters) {
            upgradeRequest = httpServletRequest;
            upgradeResponse = httpServletResponse;
            upgradeConfig = serverEndpointConfig;
            upgradePathParameters = new HashMap<>(pathParameters);
        }

        @Override
        public long getDefaultAsyncSendTimeout() {
            return asyncSendTimeout;
        }

        @Override
        public void setAsyncSendTimeout(long timeout) {
            asyncSendTimeout = timeout;
        }

        @Override
        public Session connectToServer(Object annotatedEndpointInstance, URI path)
                throws DeploymentException, IOException {
            throw new UnsupportedOperationException("Client connections are not provided by this test container");
        }

        @Override
        public Session connectToServer(Class<?> annotatedEndpointClass, URI path)
                throws DeploymentException, IOException {
            throw new UnsupportedOperationException("Client connections are not provided by this test container");
        }

        @Override
        public Session connectToServer(Endpoint endpointInstance, ClientEndpointConfig clientEndpointConfig, URI path)
                throws DeploymentException, IOException {
            throw new UnsupportedOperationException("Client connections are not provided by this test container");
        }

        @Override
        public Session connectToServer(
                Class<? extends Endpoint> endpointClass, ClientEndpointConfig clientEndpointConfig, URI path)
                throws DeploymentException, IOException {
            throw new UnsupportedOperationException("Client connections are not provided by this test container");
        }

        @Override
        public long getDefaultMaxSessionIdleTimeout() {
            return idleTimeout;
        }

        @Override
        public void setDefaultMaxSessionIdleTimeout(long timeout) {
            idleTimeout = timeout;
        }

        @Override
        public int getDefaultMaxBinaryMessageBufferSize() {
            return binaryBufferSize;
        }

        @Override
        public void setDefaultMaxBinaryMessageBufferSize(int max) {
            binaryBufferSize = max;
        }

        @Override
        public int getDefaultMaxTextMessageBufferSize() {
            return textBufferSize;
        }

        @Override
        public void setDefaultMaxTextMessageBufferSize(int max) {
            textBufferSize = max;
        }

        @Override
        public Set<Extension> getInstalledExtensions() {
            return Collections.unmodifiableSet(installedExtensions);
        }

        List<Class<?>> annotatedEndpointClasses() {
            return annotatedEndpointClasses;
        }

        List<ServerEndpointConfig> programmaticEndpointConfigs() {
            return programmaticEndpointConfigs;
        }

        Object upgradeRequest() {
            return upgradeRequest;
        }

        Object upgradeResponse() {
            return upgradeResponse;
        }

        ServerEndpointConfig upgradeConfig() {
            return upgradeConfig;
        }

        Map<String, String> upgradePathParameters() {
            return upgradePathParameters;
        }
    }

    private static final class SimpleHandshakeRequest implements HandshakeRequest {
        private final URI requestUri;
        private final Map<String, List<String>> headers;
        private final Map<String, List<String>> parameters;
        private final String queryString;
        private final Principal principal;
        private final Set<String> roles;
        private final Object httpSession;

        private SimpleHandshakeRequest(
                URI requestUri,
                Map<String, List<String>> headers,
                Map<String, List<String>> parameters,
                String queryString,
                Principal principal,
                Set<String> roles,
                Object httpSession) {
            this.requestUri = requestUri;
            this.headers = headers;
            this.parameters = parameters;
            this.queryString = queryString;
            this.principal = principal;
            this.roles = roles;
            this.httpSession = httpSession;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public boolean isUserInRole(String role) {
            return roles.contains(role);
        }

        @Override
        public Object getHttpSession() {
            return httpSession;
        }

        @Override
        public Map<String, List<String>> getParameterMap() {
            return parameters;
        }

        @Override
        public String getQueryString() {
            return queryString;
        }
    }

    private static final class SimpleHandshakeResponse implements HandshakeResponse {
        private final Map<String, List<String>> headers;

        private SimpleHandshakeResponse(Map<String, List<String>> headers) {
            this.headers = headers;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return headers;
        }
    }

    private static final class SimplePrincipal implements Principal {
        private final String name;

        private SimplePrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class SimpleExtension implements Extension {
        private final String name;
        private final List<Parameter> parameters;

        private SimpleExtension(String name, List<Parameter> parameters) {
            this.name = name;
            this.parameters = parameters;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Parameter> getParameters() {
            return parameters;
        }
    }

    private static final class SimpleParameter implements Extension.Parameter {
        private final String name;
        private final String value;

        private SimpleParameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }
    }
}
