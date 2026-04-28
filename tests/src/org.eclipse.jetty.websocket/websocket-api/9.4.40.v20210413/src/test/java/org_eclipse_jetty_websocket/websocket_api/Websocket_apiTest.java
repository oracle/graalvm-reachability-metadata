/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.websocket_api;

import org.eclipse.jetty.websocket.api.BadPayloadException;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.CloseException;
import org.eclipse.jetty.websocket.api.CloseStatus;
import org.eclipse.jetty.websocket.api.InvalidWebSocketException;
import org.eclipse.jetty.websocket.api.MessageTooLargeException;
import org.eclipse.jetty.websocket.api.PolicyViolationException;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.SuspendToken;
import org.eclipse.jetty.websocket.api.UpgradeException;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketBehavior;
import org.eclipse.jetty.websocket.api.WebSocketConstants;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.api.WebSocketTimeoutException;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.eclipse.jetty.websocket.api.extensions.Extension;
import org.eclipse.jetty.websocket.api.extensions.ExtensionConfig;
import org.eclipse.jetty.websocket.api.extensions.ExtensionFactory;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.eclipse.jetty.websocket.api.extensions.IncomingFrames;
import org.eclipse.jetty.websocket.api.extensions.OutgoingFrames;
import org.eclipse.jetty.websocket.api.util.QuoteUtil;
import org.eclipse.jetty.websocket.api.util.WSURI;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Websocket_apiTest {

    @Test
    void webSocketAdapterTracksConnectionStateAcrossConnectAndClose() {
        WebSocketAdapter adapter = new WebSocketAdapter();
        RecordingRemoteEndpoint remote = new RecordingRemoteEndpoint();
        StubSession session = new StubSession(remote);

        assertThat(adapter.isConnected()).isFalse();
        assertThat(adapter.isNotConnected()).isTrue();
        assertThat(adapter.getSession()).isNull();
        assertThat(adapter.getRemote()).isNull();

        adapter.onWebSocketConnect(session);

        assertThat(adapter.isConnected()).isTrue();
        assertThat(adapter.isNotConnected()).isFalse();
        assertThat(adapter.getSession()).isSameAs(session);
        assertThat(adapter.getRemote()).isSameAs(remote);

        session.close();

        assertThat(adapter.isConnected()).isFalse();
        assertThat(adapter.isNotConnected()).isTrue();
        assertThat(adapter.getSession()).isSameAs(session);
        assertThat(adapter.getRemote()).isSameAs(remote);

        adapter.onWebSocketClose(StatusCode.NORMAL, "done");

        assertThat(adapter.isConnected()).isFalse();
        assertThat(adapter.isNotConnected()).isTrue();
        assertThat(adapter.getSession()).isNull();
        assertThat(adapter.getRemote()).isNull();
    }

    @Test
    void webSocketPolicyValidatesLimitsAndDelegatesBehavior() {
        WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();

        assertThat(policy.getBehavior()).isEqualTo(WebSocketBehavior.CLIENT);
        assertThat(policy.getIdleTimeout()).isEqualTo(300_000L);
        assertThat(policy.getInputBufferSize()).isEqualTo(4 * 1024);
        assertThat(policy.getMaxTextMessageSize()).isEqualTo(64 * 1024);
        assertThat(policy.getMaxTextMessageBufferSize()).isEqualTo(32 * 1024);
        assertThat(policy.getMaxBinaryMessageSize()).isEqualTo(64 * 1024);
        assertThat(policy.getMaxBinaryMessageBufferSize()).isEqualTo(32 * 1024);
        assertThat(policy.delegateAs(WebSocketBehavior.CLIENT)).isSameAs(policy);

        policy.setIdleTimeout(1_000L);
        policy.setInputBufferSize(2_048);
        policy.setMaxTextMessageSize(64);
        policy.setMaxTextMessageBufferSize(48);
        policy.setMaxBinaryMessageSize(32);
        policy.setMaxBinaryMessageBufferSize(24);

        policy.assertValidTextMessageSize(64);
        policy.assertValidBinaryMessageSize(32);

        assertThatThrownBy(() -> policy.assertValidTextMessageSize(65))
                .isInstanceOf(MessageTooLargeException.class)
                .hasMessageContaining("Text message size [65] exceeds maximum size [64]");
        assertThatThrownBy(() -> policy.assertValidBinaryMessageSize(33))
                .isInstanceOf(MessageTooLargeException.class)
                .hasMessageContaining("Binary message size [33] exceeds maximum size [32]");
        policy.setIdleTimeout(0);
        assertThat(policy.getIdleTimeout()).isZero();
        policy.setIdleTimeout(1_000L);
        assertThatThrownBy(() -> policy.setInputBufferSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("InputBufferSize [0]");
        assertThatThrownBy(() -> policy.setMaxTextMessageSize(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MaxTextMessageSize [-2]");
        assertThatThrownBy(() -> policy.setAsyncWriteTimeout(1_001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AsyncWriteTimeout [1001] must be less than IdleTimeout [1000]");

        WebSocketPolicy delegated = policy.delegateAs(WebSocketBehavior.SERVER);
        assertThat(delegated).isNotSameAs(policy);
        assertThat(delegated.getBehavior()).isEqualTo(WebSocketBehavior.SERVER);
        assertThat(delegated.getIdleTimeout()).isEqualTo(1_000L);
        assertThat(delegated.getInputBufferSize()).isEqualTo(2_048);
        assertThat(delegated.getMaxTextMessageSize()).isEqualTo(64);
        assertThat(delegated.getMaxBinaryMessageSize()).isEqualTo(32);

        delegated.setMaxTextMessageSize(96);
        delegated.setMaxBinaryMessageSize(48);

        assertThat(policy.getMaxTextMessageSize()).isEqualTo(96);
        assertThat(policy.getMaxBinaryMessageSize()).isEqualTo(48);
        assertThat(policy.clonePolicy()).isNotSameAs(policy);
        assertThat(policy.clonePolicy().getBehavior()).isEqualTo(WebSocketBehavior.CLIENT);
    }

    @Test
    void extensionConfigParsesCopiesAndSerializesParameters() {
        List<ExtensionConfig> parsed = ExtensionConfig.parseList(
                "permessage-deflate; client_max_window_bits; server_max_window_bits=10",
                "x-trace; mode=\"debug;full\""
        );

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).getName()).isEqualTo("permessage-deflate");
        assertThat(parsed.get(0).getParameters())
                .containsEntry("client_max_window_bits", null)
                .containsEntry("server_max_window_bits", "10");
        assertThat(parsed.get(0).getParameter("server_max_window_bits", -1)).isEqualTo(10);
        assertThat(parsed.get(1).getName()).isEqualTo("x-trace");
        assertThat(parsed.get(1).getParameter("mode", "missing")).isEqualTo("debug;full");

        ExtensionConfig copied = new ExtensionConfig(parsed.get(1));
        parsed.get(1).setParameter("level", 7);
        copied.init(parsed.get(1));

        assertThat(copied.getParameters())
                .containsEntry("mode", "debug;full")
                .containsEntry("level", "7");

        ExtensionConfig serializable = new ExtensionConfig("x-telemetry");
        serializable.setParameter("mode", "debug;full");
        serializable.setParameter("enabled");

        assertThat(serializable.getParameterizedName()).contains("x-telemetry");
        assertThat(serializable.getParameterizedName()).contains("mode=\"debug;full\"");
        assertThat(serializable.getParameterizedName()).contains("enabled");

        String headerValue = ExtensionConfig.toHeaderValue(List.of(
                ExtensionConfig.parse("permessage-deflate; client_max_window_bits"),
                ExtensionConfig.parse("x-telemetry; mode=\"debug;full\"")
        ));
        assertThat(headerValue).isEqualTo("permessage-deflate;client_max_window_bits, x-telemetry;mode=\"debug;full\"");

        Enumeration<String> headerValues = new Vector<>(List.of(
                "permessage-deflate; server_max_window_bits=12",
                "x-telemetry; enabled"
        )).elements();
        assertThat(ExtensionConfig.parseEnum(headerValues))
                .extracting(ExtensionConfig::getName)
                .containsExactly("permessage-deflate", "x-telemetry");
    }

    @Test
    void quoteUtilSplitsEscapesQuotesAndJoinsValues() {
        assertThat(toList(QuoteUtil.splitAt("alpha, \"beta,gamma\", 'delta;epsilon' , zeta", ",")))
                .containsExactly("alpha", "beta,gamma", "delta;epsilon", "zeta");
        assertThat(QuoteUtil.dequote("\"quoted\"")).isEqualTo("quoted");
        assertThat(QuoteUtil.unescape("line\\n\\\"quoted\\\"\\\\tail")).isEqualTo("line\n\"quoted\"\\tail");
        assertThat(QuoteUtil.join(List.of("alpha", 2, true), " | ")).isEqualTo("\"alpha\" | 2 | true");
        assertThat(QuoteUtil.join(new Object[]{"alpha", 2}, ", ")).isEqualTo("\"alpha\", 2");

        StringBuilder quoted = new StringBuilder();
        QuoteUtil.quote(quoted, "hello\n\"jetty\"");
        assertThat(quoted).hasToString("\"hello\\n\\\"jetty\\\"\"");

        StringBuilder quoteIfNeeded = new StringBuilder();
        QuoteUtil.quoteIfNeeded(quoteIfNeeded, "debug;full", ";=");
        assertThat(quoteIfNeeded).hasToString("\"debug;full\"");

        StringBuilder escaped = new StringBuilder();
        QuoteUtil.escape(escaped, "tab\tpath\\name");
        assertThat(escaped).hasToString("tab\\tpath\\\\name");
    }

    @Test
    void wsUriTranslatesHttpAndWebSocketSchemesWithoutLosingStructure() throws Exception {
        URI httpUri = URI.create("http://example.com/chat/room?id=7#frag");
        URI secureHttpUri = URI.create("https://example.com/chat");
        URI websocketUri = URI.create("ws://example.com/chat/room?id=7#frag");
        URI secureWebsocketUri = URI.create("wss://example.com/chat");

        assertThat(WSURI.toWebsocket(httpUri)).isEqualTo(websocketUri);
        assertThat(WSURI.toWebsocket(secureHttpUri)).isEqualTo(secureWebsocketUri);
        assertThat(WSURI.toWebsocket("https://example.com/events", "type=updates"))
                .isEqualTo(URI.create("wss://example.com/events?type=updates"));
        assertThat(WSURI.toHttp(websocketUri)).isEqualTo(httpUri);
        assertThat(WSURI.toHttp(secureWebsocketUri)).isEqualTo(secureHttpUri);
        assertThat(WSURI.toWebsocket(websocketUri)).isSameAs(websocketUri);
        assertThat(WSURI.toHttp(httpUri)).isSameAs(httpUri);

        assertThatThrownBy(() -> WSURI.toWebsocket(URI.create("ftp://example.com/chat")))
                .isInstanceOf(URISyntaxException.class)
                .hasMessageContaining("Unrecognized HTTP scheme");
        assertThatThrownBy(() -> WSURI.toHttp(URI.create("ftp://example.com/chat")))
                .isInstanceOf(URISyntaxException.class)
                .hasMessageContaining("Unrecognized WebSocket scheme");
    }

    @Test
    void closeStatusAndStatusCodeModelProtocolRules() {
        String maxReason = "x".repeat(CloseStatus.MAX_REASON_PHRASE);
        CloseStatus closeStatus = new CloseStatus(StatusCode.NORMAL, maxReason);

        assertThat(closeStatus.getCode()).isEqualTo(StatusCode.NORMAL);
        assertThat(closeStatus.getPhrase()).isEqualTo(maxReason);
        assertThat(BatchMode.max(BatchMode.AUTO, BatchMode.OFF)).isEqualTo(BatchMode.OFF);
        assertThat(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS).isEqualTo("Sec-WebSocket-Extensions");
        assertThat(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL).isEqualTo("Sec-WebSocket-Protocol");
        assertThat(WebSocketConstants.SEC_WEBSOCKET_VERSION).isEqualTo("Sec-WebSocket-Version");
        assertThat(WebSocketConstants.SPEC_VERSION).isEqualTo(13);

        assertThat(StatusCode.isFatal(StatusCode.SERVER_ERROR)).isTrue();
        assertThat(StatusCode.isFatal(StatusCode.NORMAL)).isFalse();
        assertThat(StatusCode.isTransmittable(StatusCode.NORMAL)).isTrue();
        assertThat(StatusCode.isTransmittable(3001)).isTrue();
        assertThat(StatusCode.isTransmittable(StatusCode.NO_CODE)).isFalse();
        assertThat(StatusCode.ABNORMAL).isEqualTo(StatusCode.NO_CLOSE);

        assertThatThrownBy(() -> new CloseStatus(StatusCode.NORMAL, maxReason + "y"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phrase exceeds maximum length of " + CloseStatus.MAX_REASON_PHRASE);
    }

    @Test
    void websocketExceptionsPreserveContextForCloseAndUpgradeFailures() {
        RuntimeException cause = new RuntimeException("boom");
        URI requestUri = URI.create("ws://localhost/socket");

        CloseException closeException = new CloseException(StatusCode.SERVER_ERROR, "server error", cause);
        BadPayloadException badPayloadException = new BadPayloadException("bad payload", cause);
        MessageTooLargeException messageTooLargeException = new MessageTooLargeException(cause);
        PolicyViolationException policyViolationException = new PolicyViolationException("policy", cause);
        ProtocolException protocolException = new ProtocolException(cause);
        UpgradeException upgradeException = new UpgradeException(requestUri, 503, "unavailable", cause);
        UpgradeException handshakeFailure = new UpgradeException(requestUri, cause);
        WebSocketTimeoutException timeoutException = new WebSocketTimeoutException("timed out", cause);
        InvalidWebSocketException invalidWebSocketException = new InvalidWebSocketException("invalid endpoint", cause);

        assertThat(closeException.getStatusCode()).isEqualTo(StatusCode.SERVER_ERROR);
        assertThat(closeException).hasMessage("server error").hasCause(cause);
        assertThat(badPayloadException.getStatusCode()).isEqualTo(StatusCode.BAD_PAYLOAD);
        assertThat(badPayloadException).hasMessage("bad payload").hasCause(cause);
        assertThat(messageTooLargeException.getStatusCode()).isEqualTo(StatusCode.MESSAGE_TOO_LARGE);
        assertThat(messageTooLargeException).hasCause(cause);
        assertThat(policyViolationException.getStatusCode()).isEqualTo(StatusCode.POLICY_VIOLATION);
        assertThat(protocolException.getStatusCode()).isEqualTo(StatusCode.PROTOCOL);
        assertThat(upgradeException.getRequestURI()).isEqualTo(requestUri);
        assertThat(upgradeException.getResponseStatusCode()).isEqualTo(503);
        assertThat(upgradeException).hasMessage("unavailable").hasCause(cause);
        assertThat(handshakeFailure.getRequestURI()).isEqualTo(requestUri);
        assertThat(handshakeFailure.getResponseStatusCode()).isEqualTo(-1);
        assertThat(handshakeFailure).hasCause(cause);
        assertThat(timeoutException).hasMessage("timed out").hasCause(cause);
        assertThat(invalidWebSocketException).hasMessage("invalid endpoint").hasCause(cause);
    }

    @Test
    void extensionFactoryRegistersExtensionsAndRoutesFramesByType() {
        RecordingExtensionFactory factory = new RecordingExtensionFactory();
        factory.register("tracking", TrackingExtension.class);

        assertThat(factory.isAvailable("tracking")).isTrue();
        assertThat(factory.getExtension("tracking")).isEqualTo(TrackingExtension.class);
        assertThat(factory.getAvailableExtensions()).containsEntry("tracking", TrackingExtension.class);
        assertThat(factory.getExtensionNames()).contains("tracking");

        ExtensionConfig config = ExtensionConfig.parse("tracking; mode=record");
        TrackingExtension extension = (TrackingExtension) factory.newInstance(config);
        RecordingIncomingFrames incomingFrames = new RecordingIncomingFrames();
        RecordingOutgoingFrames outgoingFrames = new RecordingOutgoingFrames();
        RecordingWriteCallback callback = new RecordingWriteCallback();
        StubFrame textFrame = new StubFrame(Frame.Type.TEXT, "hello jetty", true);
        StubFrame pingFrame = new StubFrame(Frame.Type.PING, "ping", true);

        extension.setNextIncomingFrames(incomingFrames);
        extension.setNextOutgoingFrames(outgoingFrames);
        extension.incomingFrame(textFrame);
        extension.outgoingFrame(pingFrame, callback, BatchMode.ON);

        assertThat(extension.getConfig().getParameter("mode", "missing")).isEqualTo("record");
        assertThat(extension.getName()).isEqualTo("tracking");
        assertThat(extension.getLastIncomingFrame()).isSameAs(textFrame);
        assertThat(extension.getLastOutgoingFrame()).isSameAs(pingFrame);
        assertThat(extension.getLastBatchMode()).isEqualTo(BatchMode.ON);
        assertThat(incomingFrames.getFrame()).isSameAs(textFrame);
        assertThat(outgoingFrames.getFrame()).isSameAs(pingFrame);
        assertThat(outgoingFrames.getBatchMode()).isEqualTo(BatchMode.ON);
        assertThat(callback.isSuccess()).isTrue();
        assertThat(callback.getFailure()).isNull();

        assertThat(textFrame.hasPayload()).isTrue();
        assertThat(textFrame.getPayloadLength()).isEqualTo("hello jetty".getBytes(StandardCharsets.UTF_8).length);
        assertThat(textFrame.getType()).isEqualTo(Frame.Type.TEXT);
        assertThat(Frame.Type.from(textFrame.getOpCode())).isEqualTo(Frame.Type.TEXT);
        assertThat(textFrame.getType().isData()).isTrue();
        assertThat(pingFrame.getType().isControl()).isTrue();
        assertThat(pingFrame.isLast()).isTrue();
        assertThat(pingFrame.isMasked()).isFalse();
        assertThatThrownBy(() -> Frame.Type.from((byte) 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid Frame.Type");

        factory.unregister("tracking");
        assertThat(factory.isAvailable("tracking")).isFalse();
    }

    private static List<String> toList(Iterator<String> iterator) {
        List<String> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static final class RecordingExtensionFactory extends ExtensionFactory {
        @Override
        public Extension newInstance(ExtensionConfig config) {
            if (TrackingExtension.class.equals(getExtension(config.getName()))) {
                return new TrackingExtension(config);
            }
            return null;
        }
    }

    private static final class TrackingExtension implements Extension {
        private final ExtensionConfig config;
        private IncomingFrames nextIncomingFrames;
        private OutgoingFrames nextOutgoingFrames;
        private Frame lastIncomingFrame;
        private Frame lastOutgoingFrame;
        private BatchMode lastBatchMode;

        private TrackingExtension(ExtensionConfig config) {
            this.config = config;
        }

        @Override
        public ExtensionConfig getConfig() {
            return config;
        }

        @Override
        public String getName() {
            return config.getName();
        }

        @Override
        public boolean isRsv1User() {
            return false;
        }

        @Override
        public boolean isRsv2User() {
            return false;
        }

        @Override
        public boolean isRsv3User() {
            return false;
        }

        @Override
        public void setNextIncomingFrames(IncomingFrames nextIncomingFrames) {
            this.nextIncomingFrames = nextIncomingFrames;
        }

        @Override
        public void setNextOutgoingFrames(OutgoingFrames nextOutgoingFrames) {
            this.nextOutgoingFrames = nextOutgoingFrames;
        }

        @Override
        public void incomingFrame(Frame frame) {
            lastIncomingFrame = frame;
            nextIncomingFrames.incomingFrame(frame);
        }

        @Override
        public void outgoingFrame(Frame frame, WriteCallback callback, BatchMode batchMode) {
            lastOutgoingFrame = frame;
            lastBatchMode = batchMode;
            nextOutgoingFrames.outgoingFrame(frame, callback, batchMode);
        }

        private Frame getLastIncomingFrame() {
            return lastIncomingFrame;
        }

        private Frame getLastOutgoingFrame() {
            return lastOutgoingFrame;
        }

        private BatchMode getLastBatchMode() {
            return lastBatchMode;
        }
    }

    private static final class RecordingIncomingFrames implements IncomingFrames {
        private Frame frame;

        @Override
        public void incomingFrame(Frame frame) {
            this.frame = frame;
        }

        private Frame getFrame() {
            return frame;
        }
    }

    private static final class RecordingOutgoingFrames implements OutgoingFrames {
        private Frame frame;
        private BatchMode batchMode;

        @Override
        public void outgoingFrame(Frame frame, WriteCallback callback, BatchMode batchMode) {
            this.frame = frame;
            this.batchMode = batchMode;
            callback.writeSuccess();
        }

        private Frame getFrame() {
            return frame;
        }

        private BatchMode getBatchMode() {
            return batchMode;
        }
    }

    private static final class RecordingWriteCallback implements WriteCallback {
        private boolean success;
        private Throwable failure;

        @Override
        public void writeFailed(Throwable cause) {
            failure = cause;
        }

        @Override
        public void writeSuccess() {
            success = true;
        }

        private boolean isSuccess() {
            return success;
        }

        private Throwable getFailure() {
            return failure;
        }
    }

    private static final class StubFrame implements Frame {
        private final Frame.Type type;
        private final ByteBuffer payload;
        private final boolean fin;

        private StubFrame(Frame.Type type, String payloadText, boolean fin) {
            this.type = type;
            this.payload = ByteBuffer.wrap(payloadText.getBytes(StandardCharsets.UTF_8));
            this.fin = fin;
        }

        @Override
        public byte[] getMask() {
            return null;
        }

        @Override
        public byte getOpCode() {
            return type.getOpCode();
        }

        @Override
        public ByteBuffer getPayload() {
            return payload.asReadOnlyBuffer();
        }

        @Override
        public int getPayloadLength() {
            return payload.remaining();
        }

        @Override
        public Frame.Type getType() {
            return type;
        }

        @Override
        public boolean hasPayload() {
            return payload.hasRemaining();
        }

        @Override
        public boolean isFin() {
            return fin;
        }

        @Override
        public boolean isLast() {
            return fin;
        }

        @Override
        public boolean isMasked() {
            return false;
        }

        @Override
        public boolean isRsv1() {
            return false;
        }

        @Override
        public boolean isRsv2() {
            return false;
        }

        @Override
        public boolean isRsv3() {
            return false;
        }
    }

    private static final class StubSession implements Session {
        private final RecordingRemoteEndpoint remote;
        private final WebSocketPolicy policy = WebSocketPolicy.newClientPolicy();
        private boolean open = true;
        private long idleTimeout;
        private final InetSocketAddress localAddress = new InetSocketAddress("127.0.0.1", 8080);
        private final InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 9090);

        private StubSession(RecordingRemoteEndpoint remote) {
            this.remote = remote;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void close(CloseStatus closeStatus) {
            open = false;
        }

        @Override
        public void close(int statusCode, String reason) {
            open = false;
        }

        @Override
        public void disconnect() {
            open = false;
        }

        @Override
        public long getIdleTimeout() {
            return idleTimeout;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return localAddress;
        }

        @Override
        public WebSocketPolicy getPolicy() {
            return policy;
        }

        @Override
        public String getProtocolVersion() {
            return Integer.toString(WebSocketConstants.SPEC_VERSION);
        }

        @Override
        public RemoteEndpoint getRemote() {
            return remote;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public UpgradeRequest getUpgradeRequest() {
            return null;
        }

        @Override
        public UpgradeResponse getUpgradeResponse() {
            return null;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public void setIdleTimeout(long ms) {
            idleTimeout = ms;
        }

        @Override
        public SuspendToken suspend() {
            return () -> {
            };
        }
    }

    private static final class RecordingRemoteEndpoint implements RemoteEndpoint {
        private BatchMode batchMode = BatchMode.AUTO;
        private int maxOutgoingFrames;
        private String lastStringMessage;
        private byte[] lastBinaryMessage = new byte[0];
        private ByteBuffer lastPing = ByteBuffer.allocate(0);
        private ByteBuffer lastPong = ByteBuffer.allocate(0);

        @Override
        public void sendBytes(ByteBuffer data) {
            lastBinaryMessage = copyBytes(data);
        }

        @Override
        public Future<Void> sendBytesByFuture(ByteBuffer data) {
            sendBytes(data);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendBytes(ByteBuffer data, WriteCallback callback) {
            sendBytes(data);
            callback.writeSuccess();
        }

        @Override
        public void sendPartialBytes(ByteBuffer fragment, boolean isLast) {
            lastBinaryMessage = copyBytes(fragment);
        }

        @Override
        public void sendPartialString(String fragment, boolean isLast) {
            lastStringMessage = fragment;
        }

        @Override
        public void sendPing(ByteBuffer applicationData) {
            lastPing = applicationData.asReadOnlyBuffer();
        }

        @Override
        public void sendPong(ByteBuffer applicationData) {
            lastPong = applicationData.asReadOnlyBuffer();
        }

        @Override
        public void sendString(String text) {
            lastStringMessage = text;
        }

        @Override
        public Future<Void> sendStringByFuture(String text) {
            sendString(text);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendString(String text, WriteCallback callback) {
            sendString(text);
            callback.writeSuccess();
        }

        @Override
        public BatchMode getBatchMode() {
            return batchMode;
        }

        @Override
        public void setBatchMode(BatchMode batchMode) {
            this.batchMode = batchMode;
        }

        @Override
        public int getMaxOutgoingFrames() {
            return maxOutgoingFrames;
        }

        @Override
        public void setMaxOutgoingFrames(int maxOutgoingFrames) {
            this.maxOutgoingFrames = maxOutgoingFrames;
        }

        @Override
        public InetSocketAddress getInetSocketAddress() {
            return new InetSocketAddress("127.0.0.1", 9090);
        }

        @Override
        public void flush() {
        }

        private byte[] copyBytes(ByteBuffer buffer) {
            ByteBuffer copy = buffer.asReadOnlyBuffer();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return bytes;
        }

        @SuppressWarnings("unused")
        private String lastBinaryMessageAsString() {
            return new String(lastBinaryMessage, StandardCharsets.UTF_8);
        }
    }
}
