/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_websocket.jakarta_websocket_client_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.PongMessage;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import jakarta.websocket.SessionException;
import jakarta.websocket.WebSocketContainer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

public class Jakarta_websocket_client_apiTest {
    @Test
    void clientEndpointConfigBuilderCreatesUsableImmutableConfiguration() throws Exception {
        RecordingConfigurator configurator = new RecordingConfigurator();
        SSLContext sslContext = SSLContext.getDefault();
        Extension extension = new SimpleExtension(
                "permessage-deflate", List.of(new SimpleParameter("client_max_window_bits", "15")));
        List<String> protocols = new ArrayList<>(List.of("json", "cbor"));
        List<Extension> extensions = new ArrayList<>(List.of(extension));
        List<Class<? extends Encoder>> encoders = new ArrayList<>(
                List.of(UppercaseTextEncoder.class, Utf8BinaryEncoder.class));
        List<Class<? extends Decoder>> decoders = new ArrayList<>(
                List.of(UppercaseTextDecoder.class, Utf8BinaryDecoder.class));

        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .preferredSubprotocols(protocols)
                .extensions(extensions)
                .encoders(encoders)
                .decoders(decoders)
                .sslContext(sslContext)
                .configurator(configurator)
                .build();

        assertThat(config.getPreferredSubprotocols()).containsExactly("json", "cbor");
        assertThat(config.getExtensions()).containsExactly(extension);
        assertThat(config.getEncoders()).containsExactly(UppercaseTextEncoder.class, Utf8BinaryEncoder.class);
        assertThat(config.getDecoders()).containsExactly(UppercaseTextDecoder.class, Utf8BinaryDecoder.class);
        assertThat(config.getSSLContext()).isSameAs(sslContext);
        assertThat(config.getConfigurator()).isSameAs(configurator);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> config.getPreferredSubprotocols().add("xml"));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> config.getExtensions().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> config.getEncoders().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> config.getDecoders().clear());

        config.getUserProperties().put("traceId", "abc-123");
        assertThat(config.getUserProperties()).containsEntry("traceId", "abc-123");
    }

    @Test
    void clientEndpointConfigBuilderAcceptsNullCollectionsAsEmptyLists() {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .preferredSubprotocols(null)
                .extensions(null)
                .encoders(null)
                .decoders(null)
                .build();

        assertThat(config.getPreferredSubprotocols()).isEmpty();
        assertThat(config.getExtensions()).isEmpty();
        assertThat(config.getEncoders()).isEmpty();
        assertThat(config.getDecoders()).isEmpty();
        assertThat(config.getSSLContext()).isNull();
        assertThat(config.getConfigurator()).isNotNull();
        assertThat(config.getUserProperties()).isEmpty();
    }

    @Test
    void configuratorHooksCanMutateRequestHeadersAndObserveHandshakeResponse() {
        RecordingConfigurator configurator = new RecordingConfigurator();
        Map<String, List<String>> headers = new HashMap<>();
        HandshakeResponse response = new SimpleHandshakeResponse(Map.of(
                HandshakeResponse.SEC_WEBSOCKET_ACCEPT, List.of("accepted-key"),
                "Sec-WebSocket-Protocol", List.of("json")));

        configurator.beforeRequest(headers);
        configurator.afterResponse(response);

        assertThat(headers).containsEntry("X-Test-Client", List.of("jakarta-websocket"));
        assertThat(configurator.responseHeaders())
                .containsEntry(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, List.of("accepted-key"));
    }

    @Test
    void closeCodesMapKnownSpecificationCodesAndApplicationDefinedCodes() {
        assertThat(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode()).isEqualTo(1000);
        assertThat(CloseReason.CloseCodes.GOING_AWAY.getCode()).isEqualTo(1001);
        assertThat(CloseReason.CloseCodes.PROTOCOL_ERROR.getCode()).isEqualTo(1002);
        assertThat(CloseReason.CloseCodes.CANNOT_ACCEPT.getCode()).isEqualTo(1003);
        assertThat(CloseReason.CloseCodes.RESERVED.getCode()).isEqualTo(1004);
        assertThat(CloseReason.CloseCodes.NO_STATUS_CODE.getCode()).isEqualTo(1005);
        assertThat(CloseReason.CloseCodes.CLOSED_ABNORMALLY.getCode()).isEqualTo(1006);
        assertThat(CloseReason.CloseCodes.NOT_CONSISTENT.getCode()).isEqualTo(1007);
        assertThat(CloseReason.CloseCodes.VIOLATED_POLICY.getCode()).isEqualTo(1008);
        assertThat(CloseReason.CloseCodes.TOO_BIG.getCode()).isEqualTo(1009);
        assertThat(CloseReason.CloseCodes.NO_EXTENSION.getCode()).isEqualTo(1010);
        assertThat(CloseReason.CloseCodes.UNEXPECTED_CONDITION.getCode()).isEqualTo(1011);
        assertThat(CloseReason.CloseCodes.SERVICE_RESTART.getCode()).isEqualTo(1012);
        assertThat(CloseReason.CloseCodes.TRY_AGAIN_LATER.getCode()).isEqualTo(1013);
        assertThat(CloseReason.CloseCodes.TLS_HANDSHAKE_FAILURE.getCode()).isEqualTo(1015);

        assertThat(CloseReason.CloseCodes.getCloseCode(1000)).isSameAs(CloseReason.CloseCodes.NORMAL_CLOSURE);
        assertThat(CloseReason.CloseCodes.getCloseCode(1014).getCode()).isEqualTo(1014);
        assertThat(CloseReason.CloseCodes.getCloseCode(3001).getCode()).isEqualTo(3001);
        assertThat(CloseReason.CloseCodes.getCloseCode(4999).getCode()).isEqualTo(4999);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> CloseReason.CloseCodes.getCloseCode(999));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> CloseReason.CloseCodes.getCloseCode(5000));
    }

    @Test
    void closeReasonValidatesCodeAndUtf8ReasonLength() {
        CloseReason reason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "finished");
        CloseReason emptyReason = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "");
        CloseReason unicodeReason = new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "policy-\u2713");
        String oversizedUtf8Reason = "\u00fc".repeat(62);

        assertThat(reason.getCloseCode()).isSameAs(CloseReason.CloseCodes.NORMAL_CLOSURE);
        assertThat(reason.getReasonPhrase()).isEqualTo("finished");
        assertThat(reason).hasToString("CloseReason[1000,finished]");
        assertThat(emptyReason.getReasonPhrase()).isEmpty();
        assertThat(emptyReason).hasToString("CloseReason[1001]");
        assertThat(unicodeReason.getReasonPhrase()).isEqualTo("policy-\u2713");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new CloseReason(null, "missing"));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CloseReason(CloseReason.CloseCodes.TOO_BIG, oversizedUtf8Reason));
    }

    @Test
    void encodeDecodeExceptionsExposeOriginalPayloadAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ByteBuffer bytes = ByteBuffer.wrap(new byte[] {1, 2, 3});
        DecodeException binaryWithCause = new DecodeException(bytes, "bad bytes", cause);
        DecodeException binary = new DecodeException(bytes, "bad bytes");
        DecodeException textWithCause = new DecodeException("payload", "bad text", cause);
        DecodeException text = new DecodeException("payload", "bad text");
        EncodeException encodeWithCause = new EncodeException(42, "bad object", cause);
        EncodeException encode = new EncodeException(42, "bad object");

        assertThat(binaryWithCause.getBytes()).isSameAs(bytes);
        assertThat(binaryWithCause).hasMessage("bad bytes").hasCause(cause);
        assertThat(binary.getBytes()).isSameAs(bytes);
        assertThat(binary).hasMessage("bad bytes").hasNoCause();
        assertThat(textWithCause.getText()).isEqualTo("payload");
        assertThat(textWithCause).hasMessage("bad text").hasCause(cause);
        assertThat(text.getText()).isEqualTo("payload");
        assertThat(text).hasMessage("bad text").hasNoCause();
        assertThat(encodeWithCause.getObject()).isEqualTo(42);
        assertThat(encodeWithCause).hasMessage("bad object").hasCause(cause);
        assertThat(encode.getObject()).isEqualTo(42);
        assertThat(encode).hasMessage("bad object").hasNoCause();
    }

    @Test
    void deploymentAndSessionExceptionsExposeMessageCauseAndSession() {
        RuntimeException cause = new RuntimeException("root cause");
        InMemorySession session = new InMemorySession("session-1");
        DeploymentException deployment = new DeploymentException("deploy failed", cause);
        DeploymentException deploymentWithoutCause = new DeploymentException("deploy failed");
        SessionException sessionException = new SessionException("session failed", cause, session);

        assertThat(deployment).hasMessage("deploy failed").hasCause(cause);
        assertThat(deploymentWithoutCause).hasMessage("deploy failed").hasNoCause();
        assertThat(sessionException.getSession()).isSameAs(session);
        assertThat(sessionException).hasMessage("session failed").hasCause(cause);
    }

    @Test
    void sendResultDistinguishesSuccessfulAndFailedAsyncSends() {
        RuntimeException failure = new RuntimeException("send failed");
        SendResult ok = new SendResult();
        SendResult failed = new SendResult(failure);

        assertThat(ok.isOK()).isTrue();
        assertThat(ok.getException()).isNull();
        assertThat(failed.isOK()).isFalse();
        assertThat(failed.getException()).isSameAs(failure);
    }

    @Test
    void encoderAndDecoderContractsWorkForTextBinaryAndStreamingForms() throws Exception {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        UppercaseTextEncoder textEncoder = new UppercaseTextEncoder();
        UppercaseTextDecoder textDecoder = new UppercaseTextDecoder();
        Utf8BinaryEncoder binaryEncoder = new Utf8BinaryEncoder();
        Utf8BinaryDecoder binaryDecoder = new Utf8BinaryDecoder();
        StreamingTextEncoder streamingTextEncoder = new StreamingTextEncoder();
        StreamingTextDecoder streamingTextDecoder = new StreamingTextDecoder();
        StreamingBinaryEncoder streamingBinaryEncoder = new StreamingBinaryEncoder();
        StreamingBinaryDecoder streamingBinaryDecoder = new StreamingBinaryDecoder();

        textEncoder.init(config);
        textDecoder.init(config);
        assertThat(textEncoder.encode("hello")).isEqualTo("HELLO");
        assertThat(textDecoder.willDecode("hello")).isTrue();
        assertThat(textDecoder.willDecode("")).isFalse();
        assertThat(textDecoder.decode("HELLO")).isEqualTo("hello");

        assertThat(StandardCharsets.UTF_8.decode(binaryEncoder.encode("socket")).toString()).isEqualTo("socket");
        assertThat(binaryDecoder.willDecode(ByteBuffer.wrap("socket".getBytes(StandardCharsets.UTF_8)))).isTrue();
        assertThat(binaryDecoder.decode(ByteBuffer.wrap("socket".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("socket");

        StringWriter writer = new StringWriter();
        streamingTextEncoder.encode("stream", writer);
        assertThat(writer).hasToString("stream");
        assertThat(streamingTextDecoder.decode(new StringReader("stream"))).isEqualTo("stream");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        streamingBinaryEncoder.encode("stream", output);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("stream");
        assertThat(streamingBinaryDecoder.decode(new ByteArrayInputStream(output.toByteArray()))).isEqualTo("stream");

        textEncoder.destroy();
        textDecoder.destroy();
    }

    @Test
    void endpointOptionalLifecycleCallbacksAreNoOpsWhenNotOverridden() {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        InMemorySession session = new InMemorySession("session-open-only");
        OpenOnlyEndpoint endpoint = new OpenOnlyEndpoint();
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");

        endpoint.onOpen(session, config);
        endpoint.onClose(session, closeReason);
        endpoint.onError(session, new RuntimeException("ignored"));

        assertThat(endpoint.session()).isSameAs(session);
        assertThat(endpoint.config()).isSameAs(config);
    }

    @Test
    void endpointLifecycleAndMessageHandlersReceiveExpectedValues() throws Exception {
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        InMemorySession session = new InMemorySession("session-2");
        RecordingEndpoint endpoint = new RecordingEndpoint();
        CapturingWholeHandler wholeHandler = new CapturingWholeHandler();
        CapturingPartialHandler partialHandler = new CapturingPartialHandler();
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");
        RuntimeException error = new RuntimeException("boom");

        endpoint.onOpen(session, config);
        endpoint.onClose(session, closeReason);
        endpoint.onError(session, error);
        wholeHandler.onMessage("complete");
        partialHandler.onMessage("part-1", false);
        partialHandler.onMessage("part-2", true);

        assertThat(endpoint.session()).isSameAs(session);
        assertThat(endpoint.config()).isSameAs(config);
        assertThat(endpoint.closeReason()).isSameAs(closeReason);
        assertThat(endpoint.error()).isSameAs(error);
        assertThat(wholeHandler.messages()).containsExactly("complete");
        assertThat(partialHandler.parts()).containsExactly("part-1", "part-2");
        assertThat(partialHandler.lastFlags()).containsExactly(false, true);
    }

    @Test
    void sessionStoresHandlersPropertiesLimitsAndCloseState() throws Exception {
        InMemorySession session = new InMemorySession("session-3");
        CapturingWholeHandler wholeHandler = new CapturingWholeHandler();
        CapturingPartialHandler partialHandler = new CapturingPartialHandler();
        CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "bye");

        session.addMessageHandler(String.class, wholeHandler);
        session.addMessageHandler(String.class, partialHandler);
        session.setMaxIdleTimeout(1_000L);
        session.setMaxBinaryMessageBufferSize(64);
        session.setMaxTextMessageBufferSize(128);
        session.getUserProperties().put("authenticated", true);
        session.close(closeReason);

        assertThat(session.getMessageHandlers()).containsExactlyInAnyOrder(wholeHandler, partialHandler);
        assertThat(session.getProtocolVersion()).isEqualTo("13");
        assertThat(session.getNegotiatedSubprotocol()).isEqualTo("json");
        assertThat(session.getNegotiatedExtensions())
                .extracting(Extension::getName)
                .containsExactly("permessage-deflate");
        assertThat(session.isSecure()).isTrue();
        assertThat(session.isOpen()).isFalse();
        assertThat(session.getMaxIdleTimeout()).isEqualTo(1_000L);
        assertThat(session.getMaxBinaryMessageBufferSize()).isEqualTo(64);
        assertThat(session.getMaxTextMessageBufferSize()).isEqualTo(128);
        assertThat(session.getId()).isEqualTo("session-3");
        assertThat(session.getRequestURI()).isEqualTo(URI.create("wss://example.test/chat?room=blue"));
        assertThat(session.getRequestParameterMap()).containsEntry("room", List.of("blue"));
        assertThat(session.getQueryString()).isEqualTo("room=blue");
        assertThat(session.getPathParameters()).containsEntry("tenant", "test");
        assertThat(session.getUserProperties()).containsEntry("authenticated", true);
        assertThat(session.getUserPrincipal().getName()).isEqualTo("test-user");
        assertThat(session.getOpenSessions()).contains(session);
        assertThat(session.closeReason()).isSameAs(closeReason);

        session.removeMessageHandler(wholeHandler);
        assertThat(session.getMessageHandlers()).containsExactly(partialHandler);
    }

    @Test
    void remoteEndpointBasicAndAsyncCaptureSendsBatchingAndControlFrames() throws Exception {
        List<String> events = new ArrayList<>();
        CapturingBasicRemoteEndpoint basicEndpoint = new CapturingBasicRemoteEndpoint(events);
        CapturingAsyncRemoteEndpoint asyncEndpoint = new CapturingAsyncRemoteEndpoint(events);
        RecordingSendHandler sendHandler = new RecordingSendHandler();

        basicEndpoint.setBatchingAllowed(true);
        basicEndpoint.sendText("hello");
        basicEndpoint.sendBinary(ByteBuffer.wrap(new byte[] {7, 8}));
        basicEndpoint.sendText("partial", false);
        basicEndpoint.sendBinary(ByteBuffer.wrap(new byte[] {9}), true);
        basicEndpoint.sendObject(42);
        basicEndpoint.getSendWriter().write("writer");
        basicEndpoint.getSendWriter().close();
        basicEndpoint.getSendStream().write("stream".getBytes(StandardCharsets.UTF_8));
        basicEndpoint.getSendStream().close();
        basicEndpoint.sendPing(ByteBuffer.wrap(new byte[] {1}));
        basicEndpoint.sendPong(ByteBuffer.wrap(new byte[] {2}));
        asyncEndpoint.setSendTimeout(500L);
        Future<Void> textFuture = asyncEndpoint.sendText("async-text");
        Future<Void> binaryFuture = asyncEndpoint.sendBinary(ByteBuffer.wrap(new byte[] {3}));
        Future<Void> objectFuture = asyncEndpoint.sendObject("async-object");
        asyncEndpoint.sendText("callback-text", sendHandler);
        asyncEndpoint.sendBinary(ByteBuffer.wrap(new byte[] {4}), sendHandler);
        asyncEndpoint.sendObject("callback-object", sendHandler);
        basicEndpoint.flushBatch();

        assertThat(basicEndpoint.getBatchingAllowed()).isTrue();
        assertThat(asyncEndpoint.getSendTimeout()).isEqualTo(500L);
        assertThat(textFuture.isDone()).isTrue();
        assertThat(binaryFuture.isDone()).isTrue();
        assertThat(objectFuture.isDone()).isTrue();
        assertThat(sendHandler.results()).hasSize(3).allMatch(SendResult::isOK);
        assertThat(events).contains(
                "text:hello",
                "binary:[7, 8]",
                "partial-text:false:partial",
                "partial-binary:true:[9]",
                "object:42",
                "writer:writer",
                "stream:stream",
                "ping:[1]",
                "pong:[2]",
                "async-text:async-text",
                "async-binary:[3]",
                "async-object:async-object",
                "handler-text:callback-text",
                "handler-binary:[4]",
                "handler-object:callback-object",
                "flush");
    }

    @Test
    void extensionParameterHandshakeAndPongMessagesExposeValues() {
        Extension extension = new SimpleExtension("x-test", List.of(new SimpleParameter("flag", "true")));
        PongMessage pong = new SimplePongMessage(ByteBuffer.wrap("pong".getBytes(StandardCharsets.UTF_8)));
        HandshakeResponse response = new SimpleHandshakeResponse(Map.of("Server", List.of("test")));

        assertThat(extension.getName()).isEqualTo("x-test");
        assertThat(extension.getParameters()).hasSize(1);
        assertThat(extension.getParameters().get(0).getName()).isEqualTo("flag");
        assertThat(extension.getParameters().get(0).getValue()).isEqualTo("true");
        assertThat(StandardCharsets.UTF_8.decode(pong.getApplicationData()).toString()).isEqualTo("pong");
        assertThat(response.getHeaders()).containsEntry("Server", List.of("test"));
    }

    @Test
    void containerProviderReportsMissingServiceImplementation() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(ContainerProvider::getWebSocketContainer)
                .withMessage("Could not find an implementation class.");
    }

    @Test
    void webSocketContainerSettingsAndConnectionOverloadsAreUsable() throws Exception {
        InMemoryWebSocketContainer container = new InMemoryWebSocketContainer();
        ClientEndpointConfig config = ClientEndpointConfig.Builder.create().build();
        RecordingEndpoint endpoint = new RecordingEndpoint();
        URI uri = URI.create("wss://example.test/chat");

        container.setAsyncSendTimeout(250L);
        container.setDefaultMaxSessionIdleTimeout(1_000L);
        container.setDefaultMaxBinaryMessageBufferSize(64);
        container.setDefaultMaxTextMessageBufferSize(128);
        Session endpointSession = container.connectToServer(endpoint, config, uri);
        Session endpointClassSession = container.connectToServer(RecordingEndpoint.class, config, uri);
        Session objectSession = container.connectToServer(new AnnotatedClientEndpoint(), uri);
        Session classSession = container.connectToServer(AnnotatedClientEndpoint.class, uri);

        assertThat(container.getDefaultAsyncSendTimeout()).isEqualTo(250L);
        assertThat(container.getDefaultMaxSessionIdleTimeout()).isEqualTo(1_000L);
        assertThat(container.getDefaultMaxBinaryMessageBufferSize()).isEqualTo(64);
        assertThat(container.getDefaultMaxTextMessageBufferSize()).isEqualTo(128);
        assertThat(container.getInstalledExtensions())
                .extracting(Extension::getName)
                .containsExactly("permessage-deflate");
        assertThat(endpoint.session()).isSameAs(endpointSession);
        assertThat(endpointClassSession.getId()).isEqualTo("endpoint-instance");
        assertThat(objectSession.getId()).isEqualTo("object-endpoint");
        assertThat(classSession.getId()).isEqualTo("class-endpoint");
        assertThatExceptionOfType(DeploymentException.class)
                .isThrownBy(() -> container.connectToServer(UnsupportedEndpoint.class, config, uri));
    }

    @ClientEndpoint(
            subprotocols = {"json"},
            encoders = {UppercaseTextEncoder.class},
            decoders = {UppercaseTextDecoder.class},
            configurator = RecordingConfigurator.class)
    public static class AnnotatedClientEndpoint {
        @OnOpen
        public void onOpen(Session session) {
        }

        @OnMessage(maxMessageSize = 4096L)
        public String onMessage(String message) {
            return message;
        }

        @OnClose
        public void onClose(CloseReason reason) {
        }

        @OnError
        public void onError(Throwable throwable) {
        }
    }

    public static final class RecordingConfigurator extends ClientEndpointConfig.Configurator {
        private Map<String, List<String>> responseHeaders = Collections.emptyMap();

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            headers.put("X-Test-Client", List.of("jakarta-websocket"));
        }

        @Override
        public void afterResponse(HandshakeResponse response) {
            responseHeaders = response.getHeaders();
        }

        public Map<String, List<String>> responseHeaders() {
            return responseHeaders;
        }
    }

    public static final class SimpleExtension implements Extension {
        private final String name;
        private final List<Extension.Parameter> parameters;

        public SimpleExtension(String name, List<Extension.Parameter> parameters) {
            this.name = name;
            this.parameters = parameters;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Extension.Parameter> getParameters() {
            return parameters;
        }
    }

    public static final class SimpleParameter implements Extension.Parameter {
        private final String name;
        private final String value;

        public SimpleParameter(String name, String value) {
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

    public static final class SimpleHandshakeResponse implements HandshakeResponse {
        private final Map<String, List<String>> headers;

        public SimpleHandshakeResponse(Map<String, List<String>> headers) {
            this.headers = headers;
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return headers;
        }
    }

    public static final class SimplePongMessage implements PongMessage {
        private final ByteBuffer applicationData;

        public SimplePongMessage(ByteBuffer applicationData) {
            this.applicationData = applicationData;
        }

        @Override
        public ByteBuffer getApplicationData() {
            return applicationData.asReadOnlyBuffer();
        }
    }

    public static final class UppercaseTextEncoder implements Encoder.Text<String> {
        @Override
        public String encode(String object) {
            return object.toUpperCase();
        }
    }

    public static final class UppercaseTextDecoder implements Decoder.Text<String> {
        @Override
        public String decode(String value) {
            return value.toLowerCase();
        }

        @Override
        public boolean willDecode(String value) {
            return value != null && !value.isEmpty();
        }
    }

    public static final class Utf8BinaryEncoder implements Encoder.Binary<String> {
        @Override
        public ByteBuffer encode(String object) {
            return ByteBuffer.wrap(object.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static final class Utf8BinaryDecoder implements Decoder.Binary<String> {
        @Override
        public String decode(ByteBuffer bytes) {
            return StandardCharsets.UTF_8.decode(bytes).toString();
        }

        @Override
        public boolean willDecode(ByteBuffer bytes) {
            return bytes != null && bytes.hasRemaining();
        }
    }

    public static final class StreamingTextEncoder implements Encoder.TextStream<String> {
        @Override
        public void encode(String object, Writer writer) throws IOException {
            writer.write(object);
        }
    }

    public static final class StreamingTextDecoder implements Decoder.TextStream<String> {
        @Override
        public String decode(Reader reader) throws IOException {
            StringBuilder builder = new StringBuilder();
            int next = reader.read();
            while (next != -1) {
                builder.append((char) next);
                next = reader.read();
            }
            return builder.toString();
        }
    }

    public static final class StreamingBinaryEncoder implements Encoder.BinaryStream<String> {
        @Override
        public void encode(String object, OutputStream stream) throws IOException {
            stream.write(object.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static final class StreamingBinaryDecoder implements Decoder.BinaryStream<String> {
        @Override
        public String decode(InputStream stream) throws IOException {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static final class UnsupportedEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }

    public static final class OpenOnlyEndpoint extends Endpoint {
        private Session session;
        private EndpointConfig config;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            this.config = config;
        }

        public Session session() {
            return session;
        }

        public EndpointConfig config() {
            return config;
        }
    }

    public static final class RecordingEndpoint extends Endpoint {
        private Session session;
        private EndpointConfig config;
        private CloseReason closeReason;
        private Throwable error;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            this.config = config;
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            this.closeReason = closeReason;
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            this.error = throwable;
        }

        public Session session() {
            return session;
        }

        public EndpointConfig config() {
            return config;
        }

        public CloseReason closeReason() {
            return closeReason;
        }

        public Throwable error() {
            return error;
        }
    }

    public static final class CapturingWholeHandler implements MessageHandler.Whole<String> {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        public List<String> messages() {
            return messages;
        }
    }

    public static final class CapturingPartialHandler implements MessageHandler.Partial<String> {
        private final List<String> parts = new ArrayList<>();
        private final List<Boolean> lastFlags = new ArrayList<>();

        @Override
        public void onMessage(String partialMessage, boolean last) {
            parts.add(partialMessage);
            lastFlags.add(last);
        }

        public List<String> parts() {
            return parts;
        }

        public List<Boolean> lastFlags() {
            return lastFlags;
        }
    }

    public static final class RecordingSendHandler implements SendHandler {
        private final List<SendResult> results = new ArrayList<>();

        @Override
        public void onResult(SendResult result) {
            results.add(result);
        }

        public List<SendResult> results() {
            return results;
        }
    }

    public abstract static class AbstractCapturingRemoteEndpoint implements RemoteEndpoint {
        protected final List<String> events;
        private boolean batchingAllowed;

        protected AbstractCapturingRemoteEndpoint(List<String> events) {
            this.events = events;
        }

        @Override
        public void setBatchingAllowed(boolean allowed) {
            batchingAllowed = allowed;
        }

        @Override
        public boolean getBatchingAllowed() {
            return batchingAllowed;
        }

        @Override
        public void flushBatch() {
            events.add("flush");
        }

        @Override
        public void sendPing(ByteBuffer applicationData) {
            events.add("ping:" + bytes(applicationData));
        }

        @Override
        public void sendPong(ByteBuffer applicationData) {
            events.add("pong:" + bytes(applicationData));
        }
    }

    public static final class CapturingBasicRemoteEndpoint extends AbstractCapturingRemoteEndpoint
            implements RemoteEndpoint.Basic {
        private final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        private final StringWriter writer = new StringWriter();

        public CapturingBasicRemoteEndpoint(List<String> events) {
            super(events);
        }

        @Override
        public void sendText(String text) {
            events.add("text:" + text);
        }

        @Override
        public void sendBinary(ByteBuffer data) {
            events.add("binary:" + bytes(data));
        }

        @Override
        public void sendText(String partialMessage, boolean isLast) {
            events.add("partial-text:" + isLast + ":" + partialMessage);
        }

        @Override
        public void sendBinary(ByteBuffer partialByte, boolean isLast) {
            events.add("partial-binary:" + isLast + ":" + bytes(partialByte));
        }

        @Override
        public OutputStream getSendStream() {
            return new OutputStream() {
                @Override
                public void write(int value) {
                    stream.write(value);
                }

                @Override
                public void close() {
                    events.add("stream:" + stream.toString(StandardCharsets.UTF_8));
                }
            };
        }

        @Override
        public Writer getSendWriter() {
            return new Writer() {
                @Override
                public void write(char[] characters, int offset, int length) {
                    writer.write(characters, offset, length);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                    events.add("writer:" + writer);
                }
            };
        }

        @Override
        public void sendObject(Object data) {
            events.add("object:" + data);
        }
    }

    public static final class CapturingAsyncRemoteEndpoint extends AbstractCapturingRemoteEndpoint
            implements RemoteEndpoint.Async {
        private long sendTimeout;

        public CapturingAsyncRemoteEndpoint(List<String> events) {
            super(events);
        }

        @Override
        public long getSendTimeout() {
            return sendTimeout;
        }

        @Override
        public void setSendTimeout(long timeout) {
            sendTimeout = timeout;
        }

        @Override
        public void sendText(String text, SendHandler handler) {
            events.add("handler-text:" + text);
            handler.onResult(new SendResult());
        }

        @Override
        public Future<Void> sendText(String text) {
            events.add("async-text:" + text);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<Void> sendBinary(ByteBuffer data) {
            events.add("async-binary:" + bytes(data));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendBinary(ByteBuffer data, SendHandler handler) {
            events.add("handler-binary:" + bytes(data));
            handler.onResult(new SendResult());
        }

        @Override
        public Future<Void> sendObject(Object data) {
            events.add("async-object:" + data);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendObject(Object data, SendHandler handler) {
            events.add("handler-object:" + data);
            handler.onResult(new SendResult());
        }
    }

    public static final class InMemorySession implements Session {
        private final String id;
        private final Set<MessageHandler> handlers = new HashSet<>();
        private final Map<String, Object> userProperties = new HashMap<>();
        private final List<String> remoteEvents = new ArrayList<>();
        private final CapturingBasicRemoteEndpoint basicRemoteEndpoint = new CapturingBasicRemoteEndpoint(remoteEvents);
        private final CapturingAsyncRemoteEndpoint asyncRemoteEndpoint = new CapturingAsyncRemoteEndpoint(remoteEvents);
        private boolean open = true;
        private long maxIdleTimeout;
        private int maxBinaryMessageBufferSize;
        private int maxTextMessageBufferSize;
        private CloseReason closeReason;

        public InMemorySession(String id) {
            this.id = id;
        }

        @Override
        public WebSocketContainer getContainer() {
            return new InMemoryWebSocketContainer();
        }

        @Override
        public void addMessageHandler(MessageHandler handler) {
            handlers.add(handler);
        }

        @Override
        public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
            handlers.add(handler);
        }

        @Override
        public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
            handlers.add(handler);
        }

        @Override
        public Set<MessageHandler> getMessageHandlers() {
            return handlers;
        }

        @Override
        public void removeMessageHandler(MessageHandler handler) {
            handlers.remove(handler);
        }

        @Override
        public String getProtocolVersion() {
            return "13";
        }

        @Override
        public String getNegotiatedSubprotocol() {
            return "json";
        }

        @Override
        public List<Extension> getNegotiatedExtensions() {
            return List.of(new SimpleExtension(
                    "permessage-deflate", List.of(new SimpleParameter("server_no_context_takeover", "true"))));
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public long getMaxIdleTimeout() {
            return maxIdleTimeout;
        }

        @Override
        public void setMaxIdleTimeout(long milliseconds) {
            maxIdleTimeout = milliseconds;
        }

        @Override
        public void setMaxBinaryMessageBufferSize(int length) {
            maxBinaryMessageBufferSize = length;
        }

        @Override
        public int getMaxBinaryMessageBufferSize() {
            return maxBinaryMessageBufferSize;
        }

        @Override
        public void setMaxTextMessageBufferSize(int length) {
            maxTextMessageBufferSize = length;
        }

        @Override
        public int getMaxTextMessageBufferSize() {
            return maxTextMessageBufferSize;
        }

        @Override
        public RemoteEndpoint.Async getAsyncRemote() {
            return asyncRemoteEndpoint;
        }

        @Override
        public RemoteEndpoint.Basic getBasicRemote() {
            return basicRemoteEndpoint;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void close() {
            close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""));
        }

        @Override
        public void close(CloseReason closeReason) {
            this.closeReason = closeReason;
            open = false;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("wss://example.test/chat?room=blue");
        }

        @Override
        public Map<String, List<String>> getRequestParameterMap() {
            return Map.of("room", List.of("blue"));
        }

        @Override
        public String getQueryString() {
            return "room=blue";
        }

        @Override
        public Map<String, String> getPathParameters() {
            return Map.of("tenant", "test");
        }

        @Override
        public Map<String, Object> getUserProperties() {
            return userProperties;
        }

        @Override
        public Principal getUserPrincipal() {
            return () -> "test-user";
        }

        @Override
        public Set<Session> getOpenSessions() {
            return Set.of(this);
        }

        public CloseReason closeReason() {
            return closeReason;
        }
    }

    public static final class InMemoryWebSocketContainer implements WebSocketContainer {
        private long defaultAsyncSendTimeout;
        private long defaultMaxSessionIdleTimeout;
        private int defaultMaxBinaryMessageBufferSize;
        private int defaultMaxTextMessageBufferSize;

        @Override
        public long getDefaultAsyncSendTimeout() {
            return defaultAsyncSendTimeout;
        }

        @Override
        public void setAsyncSendTimeout(long timeout) {
            defaultAsyncSendTimeout = timeout;
        }

        @Override
        public Session connectToServer(Object annotatedEndpointInstance, URI path) {
            return new InMemorySession("object-endpoint");
        }

        @Override
        public Session connectToServer(Class<?> annotatedEndpointClass, URI path) {
            return new InMemorySession("class-endpoint");
        }

        @Override
        public Session connectToServer(Endpoint endpoint, ClientEndpointConfig config, URI path) {
            InMemorySession session = new InMemorySession("endpoint-instance");
            endpoint.onOpen(session, config);
            return session;
        }

        @Override
        public Session connectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig config, URI path)
                throws DeploymentException {
            if (endpointClass == RecordingEndpoint.class) {
                return connectToServer(new RecordingEndpoint(), config, path);
            }
            throw new DeploymentException("Unsupported endpoint class: " + endpointClass.getName());
        }

        @Override
        public long getDefaultMaxSessionIdleTimeout() {
            return defaultMaxSessionIdleTimeout;
        }

        @Override
        public void setDefaultMaxSessionIdleTimeout(long timeout) {
            defaultMaxSessionIdleTimeout = timeout;
        }

        @Override
        public int getDefaultMaxBinaryMessageBufferSize() {
            return defaultMaxBinaryMessageBufferSize;
        }

        @Override
        public void setDefaultMaxBinaryMessageBufferSize(int max) {
            defaultMaxBinaryMessageBufferSize = max;
        }

        @Override
        public int getDefaultMaxTextMessageBufferSize() {
            return defaultMaxTextMessageBufferSize;
        }

        @Override
        public void setDefaultMaxTextMessageBufferSize(int max) {
            defaultMaxTextMessageBufferSize = max;
        }

        @Override
        public Set<Extension> getInstalledExtensions() {
            return Set.of(new SimpleExtension("permessage-deflate", List.of()));
        }
    }

    private static String bytes(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return List.of(toBoxed(bytes)).toString();
    }

    private static Byte[] toBoxed(byte[] bytes) {
        Byte[] boxed = new Byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            boxed[index] = bytes[index];
        }
        return boxed;
    }
}
