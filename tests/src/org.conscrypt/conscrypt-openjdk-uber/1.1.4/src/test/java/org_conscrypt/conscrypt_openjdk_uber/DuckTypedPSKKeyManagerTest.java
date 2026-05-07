/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class DuckTypedPSKKeyManagerTest {
    private static final String PROTOCOL = "TLSv1.2";
    private static final String CIPHER_SUITE = "TLS_PSK_WITH_AES_128_CBC_SHA";
    private static final String IDENTITY_HINT = "server-hint";
    private static final String IDENTITY = "client-identity";
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;
    private static final byte[] PSK_BYTES = new byte[] {
            1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16
    };

    @Test
    void duckTypedKeyManagerDelegatesSocketPskCallbacks() throws Exception {
        RecordingDuckTypedKeyManager clientKeyManager = new RecordingDuckTypedKeyManager();
        RecordingDuckTypedKeyManager serverKeyManager = new RecordingDuckTypedKeyManager();
        SSLContext clientContext = newContext(clientKeyManager);
        SSLContext serverContext = newContext(serverKeyManager);
        SSLSocketFactory clientFactory = clientContext.getSocketFactory();
        SSLSocketFactory serverFactory = serverContext.getSocketFactory();
        Conscrypt.setUseEngineSocket(clientFactory, true);
        Conscrypt.setUseEngineSocket(serverFactory, true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            Future<Void> server = executor.submit(() -> {
                try (Socket acceptedSocket = serverSocket.accept();
                        SSLSocket accepted = (SSLSocket) serverFactory.createSocket(
                                acceptedSocket, "localhost", serverSocket.getLocalPort(), true)) {
                    configure(accepted);
                    accepted.setUseClientMode(false);
                    accepted.startHandshake();
                    accepted.getOutputStream().write(42);
                }
                return null;
            });

            try (Socket plainClient = new Socket(
                    InetAddress.getLoopbackAddress(), serverSocket.getLocalPort());
                    SSLSocket client = (SSLSocket) clientFactory.createSocket(
                            plainClient, "localhost", serverSocket.getLocalPort(), true)) {
                configure(client);
                client.setUseClientMode(true);
                client.startHandshake();
                assertThat(client.getInputStream().read()).isEqualTo(42);
            }
            server.get(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(SOCKET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    .isTrue();
        }

        // Layered sockets can be backed by either socket or engine callbacks on different JDKs.
        assertThat(serverKeyManager.totalGetKeyCalls()).isGreaterThanOrEqualTo(1);
        assertThat(clientKeyManager.totalClientIdentityCalls()).isGreaterThanOrEqualTo(1);
        assertThat(clientKeyManager.totalGetKeyCalls()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void duckTypedKeyManagerDelegatesEnginePskCallbacks() throws Exception {
        RecordingDuckTypedKeyManager clientKeyManager = new RecordingDuckTypedKeyManager();
        RecordingDuckTypedKeyManager serverKeyManager = new RecordingDuckTypedKeyManager();
        SSLEngine client = newContext(clientKeyManager).createSSLEngine("localhost", 443);
        SSLEngine server = newContext(serverKeyManager).createSSLEngine();
        client.setUseClientMode(true);
        server.setUseClientMode(false);
        configure(client);
        configure(server);

        try {
            completeEngineHandshake(client, server);
        } finally {
            client.closeOutbound();
            server.closeOutbound();
        }

        assertThat(serverKeyManager.engineServerIdentityHintCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(serverKeyManager.engineGetKeyCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(serverKeyManager.socketServerIdentityHintCalls.get()).isZero();
        assertThat(serverKeyManager.socketGetKeyCalls.get()).isZero();
        assertThat(clientKeyManager.engineClientIdentityCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(clientKeyManager.engineGetKeyCalls.get()).isGreaterThanOrEqualTo(1);
        assertThat(clientKeyManager.socketClientIdentityCalls.get()).isZero();
        assertThat(clientKeyManager.socketGetKeyCalls.get()).isZero();
    }

    private static SSLContext newContext(KeyManager keyManager) throws Exception {
        Provider provider = Conscrypt.newProvider();
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(new KeyManager[] {keyManager}, new TrustManager[0], null);
        return context;
    }

    private static void configure(SSLSocket socket) throws Exception {
        socket.setEnabledProtocols(new String[] {PROTOCOL});
        socket.setEnabledCipherSuites(new String[] {CIPHER_SUITE});
        socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
    }

    private static void configure(SSLEngine engine) {
        engine.setEnabledProtocols(new String[] {PROTOCOL});
        engine.setEnabledCipherSuites(new String[] {CIPHER_SUITE});
    }

    private static void completeEngineHandshake(SSLEngine client, SSLEngine server)
            throws SSLException {
        SSLSessionBufferSizes bufferSizes = SSLSessionBufferSizes.of(client, server);
        ByteBuffer clientToServer = ByteBuffer.allocate(bufferSizes.packetBufferSize);
        ByteBuffer serverToClient = ByteBuffer.allocate(bufferSizes.packetBufferSize);
        ByteBuffer clientApplicationData = ByteBuffer.allocate(bufferSizes.applicationBufferSize);
        ByteBuffer serverApplicationData = ByteBuffer.allocate(bufferSizes.applicationBufferSize);

        client.beginHandshake();
        server.beginHandshake();
        for (int i = 0; i < 1_000; i++) {
            boolean progressed = false;
            progressed |= runTasks(client);
            progressed |= runTasks(server);
            progressed |= unwrapAvailableData(server, clientToServer, serverApplicationData);
            progressed |= unwrapAvailableData(client, serverToClient, clientApplicationData);
            progressed |= wrapIfNeeded(client, clientToServer);
            progressed |= wrapIfNeeded(server, serverToClient);

            if (isHandshakeComplete(client) && isHandshakeComplete(server)) {
                return;
            }
            if (!progressed) {
                throw new SSLException("SSLEngine handshake made no progress. Client="
                        + client.getHandshakeStatus() + ", server=" + server.getHandshakeStatus());
            }
        }
        throw new SSLException("SSLEngine handshake did not complete within bounded iterations");
    }

    private static boolean runTasks(SSLEngine engine) {
        if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_TASK) {
            return false;
        }
        Runnable task;
        while ((task = engine.getDelegatedTask()) != null) {
            task.run();
        }
        return true;
    }

    private static boolean unwrapAvailableData(
            SSLEngine engine, ByteBuffer networkData, ByteBuffer applicationData)
            throws SSLException {
        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        if (handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP
                && handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {
            return false;
        }
        if (networkData.position() == 0
                && handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP_AGAIN) {
            return false;
        }

        networkData.flip();
        SSLEngineResult result = engine.unwrap(networkData, applicationData);
        networkData.compact();
        applicationData.clear();
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            throw new SSLException("Unexpected application buffer overflow during handshake");
        }
        return result.getStatus() != SSLEngineResult.Status.BUFFER_UNDERFLOW;
    }

    private static boolean wrapIfNeeded(SSLEngine engine, ByteBuffer networkData)
            throws SSLException {
        if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return false;
        }
        SSLEngineResult result = engine.wrap(EMPTY_BUFFER, networkData);
        if (result.getStatus() != SSLEngineResult.Status.OK) {
            throw new SSLException("Unexpected wrap result during handshake: " + result);
        }
        return true;
    }

    private static boolean isHandshakeComplete(SSLEngine engine) {
        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        return handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                || handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    public static final class RecordingDuckTypedKeyManager implements KeyManager {
        private final AtomicInteger socketServerIdentityHintCalls = new AtomicInteger();
        private final AtomicInteger engineServerIdentityHintCalls = new AtomicInteger();
        private final AtomicInteger socketClientIdentityCalls = new AtomicInteger();
        private final AtomicInteger engineClientIdentityCalls = new AtomicInteger();
        private final AtomicInteger socketGetKeyCalls = new AtomicInteger();
        private final AtomicInteger engineGetKeyCalls = new AtomicInteger();

        public String chooseServerKeyIdentityHint(Socket socket) {
            assertThat(socket).isNotNull();
            socketServerIdentityHintCalls.incrementAndGet();
            return IDENTITY_HINT;
        }

        public String chooseServerKeyIdentityHint(SSLEngine engine) {
            assertThat(engine).isNotNull();
            engineServerIdentityHintCalls.incrementAndGet();
            return IDENTITY_HINT;
        }

        public String chooseClientKeyIdentity(String identityHint, Socket socket) {
            assertThat(identityHint).isEqualTo(IDENTITY_HINT);
            assertThat(socket).isNotNull();
            socketClientIdentityCalls.incrementAndGet();
            return IDENTITY;
        }

        public String chooseClientKeyIdentity(String identityHint, SSLEngine engine) {
            assertThat(identityHint).isEqualTo(IDENTITY_HINT);
            assertThat(engine).isNotNull();
            engineClientIdentityCalls.incrementAndGet();
            return IDENTITY;
        }

        public SecretKey getKey(String identityHint, String identity, Socket socket) {
            assertThat(identity).isEqualTo(IDENTITY);
            assertThat(socket).isNotNull();
            socketGetKeyCalls.incrementAndGet();
            return new SecretKeySpec(PSK_BYTES, "RAW");
        }

        public SecretKey getKey(String identityHint, String identity, SSLEngine engine) {
            assertThat(identity).isEqualTo(IDENTITY);
            assertThat(engine).isNotNull();
            engineGetKeyCalls.incrementAndGet();
            return new SecretKeySpec(PSK_BYTES, "RAW");
        }

        private int totalClientIdentityCalls() {
            return socketClientIdentityCalls.get() + engineClientIdentityCalls.get();
        }

        private int totalGetKeyCalls() {
            return socketGetKeyCalls.get() + engineGetKeyCalls.get();
        }
    }

    private static final class SSLSessionBufferSizes {
        private final int packetBufferSize;
        private final int applicationBufferSize;

        private SSLSessionBufferSizes(int packetBufferSize, int applicationBufferSize) {
            this.packetBufferSize = packetBufferSize;
            this.applicationBufferSize = applicationBufferSize;
        }

        private static SSLSessionBufferSizes of(SSLEngine client, SSLEngine server) {
            int packetBufferSize = Math.max(
                    client.getSession().getPacketBufferSize(),
                    server.getSession().getPacketBufferSize()) * 2;
            int applicationBufferSize = Math.max(
                    client.getSession().getApplicationBufferSize(),
                    server.getSession().getApplicationBufferSize());
            return new SSLSessionBufferSizes(packetBufferSize, applicationBufferSize);
        }
    }
}
