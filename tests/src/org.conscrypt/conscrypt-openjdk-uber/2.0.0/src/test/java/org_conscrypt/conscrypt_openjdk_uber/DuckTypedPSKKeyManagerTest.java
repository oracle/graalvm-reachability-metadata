/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.SecureRandom;
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

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DuckTypedPSKKeyManagerTest {

    private static final String PSK_CIPHER_SUITE = "TLS_PSK_WITH_AES_128_CBC_SHA";
    private static final String TLS_PROTOCOL = "TLSv1.2";
    private static final byte[] SHARED_SECRET = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void invokesEngineDuckTypedPskMethodsDuringHandshake() throws Exception {
        Provider provider = Conscrypt.newProvider();
        DuckTypedKeyManager clientManager = new DuckTypedKeyManager("engine-server-hint", "engine-client-identity");
        DuckTypedKeyManager serverManager = new DuckTypedKeyManager("engine-server-hint", "engine-client-identity");

        SSLEngine clientEngine = createPskContext(provider, clientManager).createSSLEngine("localhost", 443);
        SSLEngine serverEngine = createPskContext(provider, serverManager).createSSLEngine();
        configurePskEngine(clientEngine, true);
        configurePskEngine(serverEngine, false);

        completeHandshake(clientEngine, serverEngine);

        assertThat(clientManager.engineServerHintCalls.get()).isZero();
        assertThat(clientManager.engineClientIdentityCalls.get()).isEqualTo(1);
        assertThat(clientManager.lastEngineClientHint).isEqualTo("engine-server-hint");
        assertThat(clientManager.engineKeyCalls.get()).isEqualTo(1);
        assertThat(clientManager.lastEngineKeyHint).isEqualTo("engine-server-hint");
        assertThat(clientManager.lastEngineKeyIdentity).isEqualTo("engine-client-identity");
        assertThat(clientManager.socketServerHintCalls.get()).isZero();
        assertThat(clientManager.socketClientIdentityCalls.get()).isZero();
        assertThat(clientManager.socketKeyCalls.get()).isZero();

        assertThat(serverManager.engineServerHintCalls.get()).isEqualTo(1);
        assertThat(serverManager.engineClientIdentityCalls.get()).isZero();
        assertThat(serverManager.engineKeyCalls.get()).isEqualTo(1);
        assertThat(serverManager.lastEngineKeyHint).isEqualTo("engine-server-hint");
        assertThat(serverManager.lastEngineKeyIdentity).isEqualTo("engine-client-identity");
        assertThat(serverManager.socketServerHintCalls.get()).isZero();
        assertThat(serverManager.socketClientIdentityCalls.get()).isZero();
        assertThat(serverManager.socketKeyCalls.get()).isZero();

        clientEngine.closeOutbound();
        serverEngine.closeOutbound();
    }

    @Test
    void invokesSocketDuckTypedPskMethodsDuringHandshake() throws Exception {
        Provider provider = Conscrypt.newProvider();
        DuckTypedKeyManager clientManager = new DuckTypedKeyManager("socket-server-hint", "socket-client-identity");
        DuckTypedKeyManager serverManager = new DuckTypedKeyManager("socket-server-hint", "socket-client-identity");
        SSLSocketFactory clientFactory = createPskContext(provider, clientManager).getSocketFactory();
        SSLSocketFactory serverFactory = createPskContext(provider, serverManager).getSocketFactory();

        Conscrypt.setUseEngineSocket(clientFactory, false);
        Conscrypt.setUseEngineSocket(serverFactory, false);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            InetSocketAddress loopbackAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            serverChannel.bind(loopbackAddress);
            int port = serverChannel.socket().getLocalPort();

            Future<Void> serverHandshake = executorService.submit(() -> {
                try (SocketChannel acceptedChannel = serverChannel.accept()) {
                    Socket acceptedSocket = acceptedChannel.socket();
                    try (SSLSocket serverSocket = (SSLSocket) serverFactory.createSocket(
                            acceptedSocket,
                            acceptedSocket.getInetAddress().getHostAddress(),
                            acceptedSocket.getPort(),
                            true)) {
                        configurePskSocket(serverSocket, false);
                        serverSocket.startHandshake();
                        assertThat(serverSocket.getInputStream().read()).isEqualTo(0x11);
                        serverSocket.getOutputStream().write(0x22);
                        serverSocket.getOutputStream().flush();
                    }
                }
                return null;
            });

            try (SocketChannel clientChannel = SocketChannel.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
                Socket clientSocket = clientChannel.socket();
                try (SSLSocket sslClientSocket = (SSLSocket) clientFactory.createSocket(clientSocket, "localhost", port, true)) {
                    configurePskSocket(sslClientSocket, true);
                    sslClientSocket.startHandshake();
                    sslClientSocket.getOutputStream().write(0x11);
                    sslClientSocket.getOutputStream().flush();
                    assertThat(sslClientSocket.getInputStream().read()).isEqualTo(0x22);
                }
            }

            serverHandshake.get(30, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }

        assertThat(clientManager.socketServerHintCalls.get()).isZero();
        assertThat(clientManager.socketClientIdentityCalls.get()).isEqualTo(1);
        assertThat(clientManager.lastSocketClientHint).isEqualTo("socket-server-hint");
        assertThat(clientManager.socketKeyCalls.get()).isEqualTo(1);
        assertThat(clientManager.lastSocketKeyHint).isEqualTo("socket-server-hint");
        assertThat(clientManager.lastSocketKeyIdentity).isEqualTo("socket-client-identity");
        assertThat(clientManager.engineServerHintCalls.get()).isZero();
        assertThat(clientManager.engineClientIdentityCalls.get()).isZero();
        assertThat(clientManager.engineKeyCalls.get()).isZero();

        assertThat(serverManager.socketServerHintCalls.get()).isEqualTo(1);
        assertThat(serverManager.socketClientIdentityCalls.get()).isZero();
        assertThat(serverManager.socketKeyCalls.get()).isEqualTo(1);
        assertThat(serverManager.lastSocketKeyHint).isEqualTo("socket-server-hint");
        assertThat(serverManager.lastSocketKeyIdentity).isEqualTo("socket-client-identity");
        assertThat(serverManager.engineServerHintCalls.get()).isZero();
        assertThat(serverManager.engineClientIdentityCalls.get()).isZero();
        assertThat(serverManager.engineKeyCalls.get()).isZero();
    }

    private static SSLContext createPskContext(Provider provider, DuckTypedKeyManager keyManager) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS", provider);
        sslContext.init(new KeyManager[] {keyManager}, null, new SecureRandom());
        return sslContext;
    }

    private static void configurePskEngine(SSLEngine engine, boolean useClientMode) {
        engine.setUseClientMode(useClientMode);
        engine.setEnabledProtocols(new String[] {TLS_PROTOCOL});
        engine.setEnabledCipherSuites(new String[] {PSK_CIPHER_SUITE});
    }

    private static void configurePskSocket(SSLSocket socket, boolean useClientMode) {
        socket.setUseClientMode(useClientMode);
        socket.setEnabledProtocols(new String[] {TLS_PROTOCOL});
        socket.setEnabledCipherSuites(new String[] {PSK_CIPHER_SUITE});
    }

    private static void completeHandshake(SSLEngine firstEngine, SSLEngine secondEngine) throws SSLException {
        ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
        int packetBufferSize = Math.max(firstEngine.getSession().getPacketBufferSize(), secondEngine.getSession().getPacketBufferSize()) * 4;
        int applicationBufferSize = Math.max(
                firstEngine.getSession().getApplicationBufferSize(),
                secondEngine.getSession().getApplicationBufferSize()) * 4;
        ByteBuffer firstToSecond = ByteBuffer.allocate(packetBufferSize);
        ByteBuffer secondToFirst = ByteBuffer.allocate(packetBufferSize);
        ByteBuffer firstApplicationBuffer = ByteBuffer.allocate(applicationBufferSize);
        ByteBuffer secondApplicationBuffer = ByteBuffer.allocate(applicationBufferSize);

        firstEngine.beginHandshake();
        secondEngine.beginHandshake();

        for (int iteration = 0; iteration < 200 && !handshakeComplete(firstEngine, secondEngine); iteration++) {
            boolean progressed = false;
            progressed |= runDelegatedTasks(firstEngine);
            progressed |= runDelegatedTasks(secondEngine);
            progressed |= wrapHandshakeData(firstEngine, firstToSecond, emptyBuffer);
            progressed |= wrapHandshakeData(secondEngine, secondToFirst, emptyBuffer);
            progressed |= unwrapHandshakeData(secondEngine, firstToSecond, secondApplicationBuffer);
            progressed |= unwrapHandshakeData(firstEngine, secondToFirst, firstApplicationBuffer);

            if (!progressed) {
                throw new SSLException("Handshake did not make progress");
            }
        }

        assertThat(handshakeComplete(firstEngine, secondEngine)).isTrue();
    }

    private static boolean handshakeComplete(SSLEngine firstEngine, SSLEngine secondEngine) {
        return handshakeFinished(firstEngine.getHandshakeStatus()) && handshakeFinished(secondEngine.getHandshakeStatus());
    }

    private static boolean handshakeFinished(SSLEngineResult.HandshakeStatus handshakeStatus) {
        return handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED
                || handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    private static boolean runDelegatedTasks(SSLEngine engine) {
        boolean ranTask = false;
        while (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable delegatedTask = engine.getDelegatedTask();
            if (delegatedTask == null) {
                break;
            }
            delegatedTask.run();
            ranTask = true;
        }
        return ranTask;
    }

    private static boolean wrapHandshakeData(SSLEngine engine, ByteBuffer networkBuffer, ByteBuffer sourceBuffer)
            throws SSLException {
        if (engine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return false;
        }
        SSLEngineResult result = engine.wrap(sourceBuffer.duplicate(), networkBuffer);
        assertThat(result.getStatus()).isIn(SSLEngineResult.Status.OK, SSLEngineResult.Status.CLOSED);
        return result.bytesProduced() > 0 || result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP;
    }

    private static boolean unwrapHandshakeData(SSLEngine engine, ByteBuffer networkBuffer, ByteBuffer applicationBuffer)
            throws SSLException {
        if (networkBuffer.position() == 0) {
            return false;
        }
        networkBuffer.flip();
        SSLEngineResult result = Conscrypt.unwrap(engine, new ByteBuffer[] {networkBuffer}, new ByteBuffer[] {applicationBuffer});
        networkBuffer.compact();
        assertThat(result.getStatus()).isIn(
                SSLEngineResult.Status.OK,
                SSLEngineResult.Status.BUFFER_UNDERFLOW,
                SSLEngineResult.Status.CLOSED);
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            throw new SSLException("Application buffer overflow during handshake");
        }
        return result.bytesConsumed() > 0 || result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    public static final class DuckTypedKeyManager implements KeyManager {

        private final String serverHint;
        private final String clientIdentity;
        private final SecretKey secretKey;
        private final AtomicInteger socketServerHintCalls = new AtomicInteger();
        private final AtomicInteger engineServerHintCalls = new AtomicInteger();
        private final AtomicInteger socketClientIdentityCalls = new AtomicInteger();
        private final AtomicInteger engineClientIdentityCalls = new AtomicInteger();
        private final AtomicInteger socketKeyCalls = new AtomicInteger();
        private final AtomicInteger engineKeyCalls = new AtomicInteger();
        private volatile String lastSocketClientHint;
        private volatile String lastEngineClientHint;
        private volatile String lastSocketKeyHint;
        private volatile String lastSocketKeyIdentity;
        private volatile String lastEngineKeyHint;
        private volatile String lastEngineKeyIdentity;

        private DuckTypedKeyManager(String serverHint, String clientIdentity) {
            this.serverHint = serverHint;
            this.clientIdentity = clientIdentity;
            this.secretKey = new SecretKeySpec(SHARED_SECRET, "RAW");
        }

        public String chooseServerKeyIdentityHint(Socket socket) {
            this.socketServerHintCalls.incrementAndGet();
            return this.serverHint;
        }

        public String chooseServerKeyIdentityHint(SSLEngine engine) {
            this.engineServerHintCalls.incrementAndGet();
            return this.serverHint;
        }

        public String chooseClientKeyIdentity(String identityHint, Socket socket) {
            this.socketClientIdentityCalls.incrementAndGet();
            this.lastSocketClientHint = identityHint;
            return this.clientIdentity;
        }

        public String chooseClientKeyIdentity(String identityHint, SSLEngine engine) {
            this.engineClientIdentityCalls.incrementAndGet();
            this.lastEngineClientHint = identityHint;
            return this.clientIdentity;
        }

        public SecretKey getKey(String identityHint, String identity, Socket socket) {
            this.socketKeyCalls.incrementAndGet();
            this.lastSocketKeyHint = identityHint;
            this.lastSocketKeyIdentity = identity;
            return this.secretKey;
        }

        public SecretKey getKey(String identityHint, String identity, SSLEngine engine) {
            this.engineKeyCalls.incrementAndGet();
            this.lastEngineKeyHint = identityHint;
            this.lastEngineKeyIdentity = identity;
            return this.secretKey;
        }
    }
}
