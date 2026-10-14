/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_java_websocket.Java_WebSocket;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.Opcode;
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class Java_WebSocketTest {
    private static final long WAIT_SECONDS = 10;
    private static final int NETWORK_TIMEOUT_MILLIS = 10_000;
    private static final char[] KEY_PASSWORD = "websocket-test".toCharArray();
    private static final String PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgnHZDTVkJLNJfiEJhsA3t"
                    + "fh60vSzIJv2TN8B8IViUP2ShRANCAASkKLbnxbZ8A55rjkfuFxhkUiR4zu0c4HJz"
                    + "+ojUWXvNzHHSrCMCXd97CB2aj2Rvcf/wAWtXgbz1mXeIie+U53Qr";
    private static final String CERTIFICATE =
            "MIIBmjCCAUGgAwIBAgIUDJtdSyrNhdJXhV4FnC+2gLt1OkQwCgYIKoZIzj0EAwIwFDES"
                    + "MBAGA1UEAwwJbG9jYWxob3N0MCAXDTI2MTAxNDAyMTM1MloYDzIxMjYwOTIwMDIxMz"
                    + "UyWjAUMRIwEAYDVQQDDAlsb2NhbGhvc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNC"
                    + "AASkKLbnxbZ8A55rjkfuFxhkUiR4zu0c4HJz+ojUWXvNzHHSrCMCXd97CB2aj2Rvcf"
                    + "/wAWtXgbz1mXeIie+U53Qro28wbTAdBgNVHQ4EFgQUwY960J17JcA1xn8LtgTISjDn"
                    + "b34wHwYDVR0jBBgwFoAUwY960J17JcA1xn8LtgTISjDnb34wDwYDVR0TAQH/BAUwAw"
                    + "EB/zAaBgNVHREEEzARgglsb2NhbGhvc3SHBH8AAAEwCgYIKoZIzj0EAwIDRwAwRAIg"
                    + "N3vWT5Y5SzmqmE9VARkU5+QkW+BHy4BPeNVbsiSXfoUCIDyMYpaFRb0UZCQFBU34EF"
                    + "oN64e51MHRWr4JywIJSzbP";

    @Test
    @Timeout(value = 55)
    void clientAndServerNegotiateAndExchangeWebSocketFrames() throws Exception {
        RecordingServer server = new RecordingServer();
        RecordingClient client = null;
        try {
            server.start();
            assertThat(server.started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.error.get()).isNull();

            URI endpoint = URI.create("ws://127.0.0.1:" + server.getPort() + "/messages?tenant=acme");
            client = new RecordingClient(endpoint);
            assertThat(client.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.opened.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            WebSocket serverConnection = server.connection.get();
            assertThat(serverConnection).isNotNull();
            assertThat(server.resourceDescriptor.get()).isEqualTo("/messages?tenant=acme");
            assertThat(server.requestHeader.get()).isEqualTo("integration-test");
            assertThat(serverConnection.<String>getAttachment()).isEqualTo("session-1");
            assertThat(serverConnection.getProtocol().getProvidedProtocol()).isEqualTo("chat");
            assertThat(serverConnection.hasSSLSupport()).isFalse();
            assertThat(serverConnection.getLocalSocketAddress()).isNotNull();
            assertThat(serverConnection.getRemoteSocketAddress()).isNotNull();
            assertThat(client.openHandshake.get()).isNotNull();
            assertThat(client.openHandshake.get().getHttpStatus()).isEqualTo((short) 101);
            assertThat(client.getConnection().getProtocol().getProvidedProtocol()).isEqualTo("chat");

            String compressedMessage = "compressible-payload-".repeat(200);
            client.send(compressedMessage);
            assertThat(client.textMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("echo:" + compressedMessage);

            byte[] binaryPayload = "binary-payload".getBytes(StandardCharsets.UTF_8);
            client.send(binaryPayload);
            assertThat(client.binaryMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS)).containsExactly(binaryPayload);

            client.sendFragmentedFrame(Opcode.TEXT, StandardCharsets.UTF_8.encode("fragmented-"), false);
            client.sendFragmentedFrame(Opcode.TEXT, StandardCharsets.UTF_8.encode("message"), true);
            assertThat(client.textMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("echo:fragmented-message");

            server.broadcast("server-broadcast");
            assertThat(client.textMessages.poll(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo("server-broadcast");

            client.sendPing();
            assertThat(client.pongReceived.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            client.close(CloseFrame.NORMAL, "complete");
            assertThat(client.closed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.closed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(client.closeCode).isEqualTo(CloseFrame.NORMAL);
            assertThat(client.closeReason).isEqualTo("complete");
            assertThat(server.closeCode).isEqualTo(CloseFrame.NORMAL);
            assertThat(server.closeReason).isEqualTo("complete");
            assertThat(client.error.get()).isNull();
            assertThat(server.error.get()).isNull();
        } finally {
            if (client != null && !client.isClosed()) {
                client.close();
                client.closed.await(WAIT_SECONDS, TimeUnit.SECONDS);
            }
            server.stop(NETWORK_TIMEOUT_MILLIS);
        }
    }

    @Test
    @Timeout(value = 55)
    void clientAndServerExchangeMessagesOverTls() throws Exception {
        SecureServer server = new SecureServer(serverSslContext());
        SecureClient client = null;
        try {
            server.start();
            assertThat(server.started.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.error.get()).isNull();

            URI endpoint = URI.create("wss://127.0.0.1:" + server.getPort() + "/secure");
            client = new SecureClient(endpoint, clientSslContext());
            assertThat(client.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(client.opened.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(server.opened.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            assertThat(client.hasSSLSupport()).isTrue();
            assertThat(client.getSSLSession().getProtocol()).startsWith("TLS");
            assertThat(server.connection.get().hasSSLSupport()).isTrue();
            assertThat(server.connection.get().getSSLSession().getProtocol()).startsWith("TLS");

            client.send("confidential-message");
            assertThat(client.messages.poll(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("secure-ack:confidential-message");
            assertThat(client.error.get()).isNull();
            assertThat(server.error.get()).isNull();
        } finally {
            if (client != null && !client.isClosed()) {
                client.close();
                client.closed.await(WAIT_SECONDS, TimeUnit.SECONDS);
            }
            server.stop(NETWORK_TIMEOUT_MILLIS);
        }
    }

    private static SSLContext serverSslContext() throws Exception {
        Certificate certificate = certificate();
        PrivateKey privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY)));
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", privateKey, KEY_PASSWORD, new Certificate[] {certificate});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEY_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, null);
        return context;
    }

    private static SSLContext clientSslContext() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", certificate());

        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagerFactory.getTrustManagers(), null);
        return context;
    }

    private static Certificate certificate() throws Exception {
        byte[] encoded = Base64.getDecoder().decode(CERTIFICATE);
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static Draft compressedChatDraft() {
        PerMessageDeflateExtension extension = new PerMessageDeflateExtension();
        extension.setThreshold(64);
        return new Draft_6455(List.of(extension), List.of(new Protocol("chat")));
    }

    private static byte[] copyRemaining(ByteBuffer source) {
        ByteBuffer copy = source.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static final class SecureServer extends WebSocketServer {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch opened = new CountDownLatch(1);
        private final AtomicReference<WebSocket> connection = new AtomicReference<>();
        private final AtomicReference<Exception> error = new AtomicReference<>();

        private SecureServer(SSLContext context) {
            super(new InetSocketAddress("127.0.0.1", 0));
            setWebSocketFactory(new DefaultSSLWebSocketServerFactory(context));
        }

        @Override
        public void onStart() {
            started.countDown();
        }

        @Override
        public void onOpen(WebSocket socket, ClientHandshake handshake) {
            connection.set(socket);
            opened.countDown();
        }

        @Override
        public void onMessage(WebSocket socket, String message) {
            socket.send("secure-ack:" + message);
        }

        @Override
        public void onClose(WebSocket socket, int code, String reason, boolean remote) {
            connection.compareAndSet(socket, null);
        }

        @Override
        public void onError(WebSocket socket, Exception exception) {
            error.compareAndSet(null, exception);
            started.countDown();
            opened.countDown();
        }
    }

    private static final class SecureClient extends WebSocketClient {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CountDownLatch opened = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<Exception> error = new AtomicReference<>();

        private SecureClient(URI endpoint, SSLContext context) {
            super(endpoint);
            setSocketFactory(context.getSocketFactory());
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            opened.countDown();
        }

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closed.countDown();
        }

        @Override
        public void onError(Exception exception) {
            error.compareAndSet(null, exception);
            opened.countDown();
        }
    }

    private static final class RecordingServer extends WebSocketServer {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch opened = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<WebSocket> connection = new AtomicReference<>();
        private final AtomicReference<String> resourceDescriptor = new AtomicReference<>();
        private final AtomicReference<String> requestHeader = new AtomicReference<>();
        private final AtomicReference<Exception> error = new AtomicReference<>();
        private volatile int closeCode;
        private volatile String closeReason;

        private RecordingServer() {
            super(new InetSocketAddress("127.0.0.1", 0), 1, List.of(compressedChatDraft()));
        }

        @Override
        public void onStart() {
            started.countDown();
        }

        @Override
        public void onOpen(WebSocket socket, ClientHandshake handshake) {
            socket.setAttachment("session-1");
            connection.set(socket);
            resourceDescriptor.set(handshake.getResourceDescriptor());
            requestHeader.set(handshake.getFieldValue("X-Test-Client"));
            opened.countDown();
        }

        @Override
        public void onMessage(WebSocket socket, String message) {
            socket.send("echo:" + message);
        }

        @Override
        public void onMessage(WebSocket socket, ByteBuffer message) {
            socket.send(copyRemaining(message));
        }

        @Override
        public void onClose(WebSocket socket, int code, String reason, boolean remote) {
            closeCode = code;
            closeReason = reason;
            closed.countDown();
        }

        @Override
        public void onError(WebSocket socket, Exception exception) {
            error.compareAndSet(null, exception);
            started.countDown();
        }
    }

    private static final class RecordingClient extends WebSocketClient {
        private final BlockingQueue<String> textMessages = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> binaryMessages = new LinkedBlockingQueue<>();
        private final CountDownLatch pongReceived = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicReference<ServerHandshake> openHandshake = new AtomicReference<>();
        private final AtomicReference<Exception> error = new AtomicReference<>();
        private volatile int closeCode;
        private volatile String closeReason;

        private RecordingClient(URI endpoint) {
            super(endpoint, compressedChatDraft(), Map.of("X-Test-Client", "integration-test"),
                    NETWORK_TIMEOUT_MILLIS);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            openHandshake.set(handshake);
        }

        @Override
        public void onMessage(String message) {
            textMessages.add(message);
        }

        @Override
        public void onMessage(ByteBuffer message) {
            binaryMessages.add(copyRemaining(message));
        }

        @Override
        public void onWebsocketPong(WebSocket socket, Framedata frame) {
            super.onWebsocketPong(socket, frame);
            pongReceived.countDown();
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
            closeReason = reason;
            closed.countDown();
        }

        @Override
        public void onError(Exception exception) {
            error.compareAndSet(null, exception);
        }
    }
}
