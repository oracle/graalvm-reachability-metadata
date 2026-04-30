/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
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
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class DuckTypedPSKKeyManagerTest {
    private static final byte[] PSK_BYTES = new byte[] {
            0x01, 0x23, 0x45, 0x67, 0x11, 0x22, 0x33, 0x44,
            0x55, 0x66, 0x12, 0x34, 0x56, 0x78, 0x21, 0x43
    };
    private static final SecretKey PSK_KEY = new SecretKeySpec(PSK_BYTES, "RAW");
    private static final String PROTOCOL = "TLSv1.2";
    private static final List<String> PREFERRED_PSK_CIPHER_SUITES = List.of(
            "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_PSK_WITH_AES_128_CBC_SHA");

    @Test
    void socketHandshakeUsesDuckTypedPskKeyManager() throws Exception {
        Provider provider = Conscrypt.newProvider();
        DuckTypedKeyManager clientKeyManager = new DuckTypedKeyManager("client-socket");
        DuckTypedKeyManager serverKeyManager = new DuckTypedKeyManager("server-socket");
        SSLContext clientContext = newPskContext(provider, clientKeyManager);
        SSLContext serverContext = newPskContext(provider, serverKeyManager);
        String cipherSuite = choosePskCipherSuite(
                clientContext.createSSLEngine().getSupportedCipherSuites());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            int port = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            Future<byte[]> serverResult = executor.submit(
                    () -> runPskSocketServer(serverContext, serverChannel, cipherSuite));

            try (SocketChannel clientChannel = SocketChannel.open(
                         new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
                    SSLSocket clientSocket = (SSLSocket) clientContext.getSocketFactory()
                            .createSocket(clientChannel.socket(), "localhost", port, true)) {
                clientSocket.setEnabledProtocols(new String[] {PROTOCOL});
                clientSocket.setEnabledCipherSuites(new String[] {cipherSuite});
                clientSocket.startHandshake();
                OutputStream output = clientSocket.getOutputStream();
                output.write(7);
                output.flush();
                assertEquals(11, clientSocket.getInputStream().read());
            }
            assertArrayEquals(new byte[] {7}, serverResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, serverKeyManager.socketServerHintCalls.get());
        assertEquals(1, clientKeyManager.socketClientIdentityCalls.get());
        assertEquals(1, clientKeyManager.socketKeyCalls.get());
        assertEquals(1, serverKeyManager.socketKeyCalls.get());
    }

    @Test
    void engineHandshakeUsesDuckTypedPskKeyManager() throws Exception {
        Provider provider = Conscrypt.newProvider();
        DuckTypedKeyManager clientKeyManager = new DuckTypedKeyManager("client-engine");
        DuckTypedKeyManager serverKeyManager = new DuckTypedKeyManager("server-engine");
        SSLContext clientContext = newPskContext(provider, clientKeyManager);
        SSLContext serverContext = newPskContext(provider, serverKeyManager);
        String cipherSuite = choosePskCipherSuite(
                clientContext.createSSLEngine().getSupportedCipherSuites());

        SSLEngine clientEngine = clientContext.createSSLEngine("localhost", 443);
        clientEngine.setUseClientMode(true);
        clientEngine.setEnabledProtocols(new String[] {PROTOCOL});
        clientEngine.setEnabledCipherSuites(new String[] {cipherSuite});

        SSLEngine serverEngine = serverContext.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setEnabledProtocols(new String[] {PROTOCOL});
        serverEngine.setEnabledCipherSuites(new String[] {cipherSuite});

        completeHandshake(clientEngine, serverEngine);

        assertEquals(1, serverKeyManager.engineServerHintCalls.get());
        assertEquals(1, clientKeyManager.engineClientIdentityCalls.get());
        assertEquals(1, clientKeyManager.engineKeyCalls.get());
        assertEquals(1, serverKeyManager.engineKeyCalls.get());
    }

    private static SSLContext newPskContext(Provider provider, KeyManager keyManager)
            throws Exception {
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(new KeyManager[] {keyManager}, null, new SecureRandom());
        return context;
    }

    private static byte[] runPskSocketServer(SSLContext serverContext,
            ServerSocketChannel serverChannel, String cipherSuite) throws Exception {
        try (SocketChannel acceptedChannel = serverChannel.accept();
                SSLSocket server = (SSLSocket) serverContext.getSocketFactory().createSocket(
                        acceptedChannel.socket(), "localhost", acceptedChannel.socket().getPort(),
                        true)) {
            server.setUseClientMode(false);
            server.setEnabledProtocols(new String[] {PROTOCOL});
            server.setEnabledCipherSuites(new String[] {cipherSuite});
            server.startHandshake();
            InputStream input = server.getInputStream();
            int received = input.read();
            OutputStream output = server.getOutputStream();
            output.write(11);
            output.flush();
            return new byte[] {(byte) received};
        }
    }

    private static String choosePskCipherSuite(String[] supportedCipherSuites) {
        List<String> supported = Arrays.asList(supportedCipherSuites);
        for (String cipherSuite : PREFERRED_PSK_CIPHER_SUITES) {
            if (supported.contains(cipherSuite)) {
                return cipherSuite;
            }
        }
        fail("Conscrypt provider does not expose any expected PSK cipher suite: " + supported);
        return null;
    }

    private static void completeHandshake(SSLEngine clientEngine, SSLEngine serverEngine)
            throws Exception {
        SSLSession clientSession = clientEngine.getSession();
        SSLSession serverSession = serverEngine.getSession();
        ByteBufferPair clientToServer = new ByteBufferPair(clientSession.getPacketBufferSize(),
                serverSession.getApplicationBufferSize());
        ByteBufferPair serverToClient = new ByteBufferPair(serverSession.getPacketBufferSize(),
                clientSession.getApplicationBufferSize());

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();
        for (int i = 0; i < 100; i++) {
            runDelegatedTasks(clientEngine);
            runDelegatedTasks(serverEngine);
            boolean progressed = false;
            progressed |= wrapAndUnwrapWhenNeeded(clientEngine, serverEngine, clientToServer);
            progressed |= wrapAndUnwrapWhenNeeded(serverEngine, clientEngine, serverToClient);
            if (isHandshakeComplete(clientEngine) && isHandshakeComplete(serverEngine)) {
                return;
            }
            assertFalse(!progressed && bothNeedUnwrap(clientEngine, serverEngine),
                    "SSLEngine handshake stalled with both engines waiting for network input");
        }
        fail("SSLEngine PSK handshake did not finish");
    }

    private static boolean wrapAndUnwrapWhenNeeded(SSLEngine wrappingEngine,
            SSLEngine unwrappingEngine, ByteBufferPair buffers) throws Exception {
        if (wrappingEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            return false;
        }
        buffers.network.clear();
        SSLEngineResult wrapResult = wrappingEngine.wrap(
                ByteBufferPair.EMPTY_APPLICATION, buffers.network);
        assertNotNull(wrapResult);
        runDelegatedTasks(wrappingEngine);

        buffers.network.flip();
        while (buffers.network.hasRemaining()) {
            buffers.application.clear();
            SSLEngineResult unwrapResult = unwrappingEngine.unwrap(
                    buffers.network, buffers.application);
            assertNotNull(unwrapResult);
            runDelegatedTasks(unwrappingEngine);
        }
        return wrapResult.bytesProduced() > 0;
    }

    private static void runDelegatedTasks(SSLEngine engine) {
        Runnable task = engine.getDelegatedTask();
        while (task != null) {
            task.run();
            task = engine.getDelegatedTask();
        }
    }

    private static boolean isHandshakeComplete(SSLEngine engine) {
        SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
        return status == SSLEngineResult.HandshakeStatus.FINISHED
                || status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    private static boolean bothNeedUnwrap(SSLEngine clientEngine, SSLEngine serverEngine) {
        return clientEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP
                && serverEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
    }

    public static final class DuckTypedKeyManager implements KeyManager {
        private final String identity;
        private final AtomicInteger socketServerHintCalls = new AtomicInteger();
        private final AtomicInteger engineServerHintCalls = new AtomicInteger();
        private final AtomicInteger socketClientIdentityCalls = new AtomicInteger();
        private final AtomicInteger engineClientIdentityCalls = new AtomicInteger();
        private final AtomicInteger socketKeyCalls = new AtomicInteger();
        private final AtomicInteger engineKeyCalls = new AtomicInteger();

        DuckTypedKeyManager(String identity) {
            this.identity = identity;
        }

        public String chooseServerKeyIdentityHint(Socket socket) {
            socketServerHintCalls.incrementAndGet();
            return "hint";
        }

        public String chooseServerKeyIdentityHint(SSLEngine engine) {
            engineServerHintCalls.incrementAndGet();
            return "hint";
        }

        public String chooseClientKeyIdentity(String identityHint, Socket socket) {
            socketClientIdentityCalls.incrementAndGet();
            return identity;
        }

        public String chooseClientKeyIdentity(String identityHint, SSLEngine engine) {
            engineClientIdentityCalls.incrementAndGet();
            return identity;
        }

        public SecretKey getKey(String identityHint, String identity, Socket socket) {
            assertNotNull(identity);
            socketKeyCalls.incrementAndGet();
            return PSK_KEY;
        }

        public SecretKey getKey(String identityHint, String identity, SSLEngine engine) {
            assertNotNull(identity);
            engineKeyCalls.incrementAndGet();
            return PSK_KEY;
        }
    }

    private static final class ByteBufferPair {
        private static final ByteBuffer EMPTY_APPLICATION = ByteBuffer.allocate(0);

        private final ByteBuffer network;
        private final ByteBuffer application;

        private ByteBufferPair(int networkCapacity, int applicationCapacity) {
            network = ByteBuffer.allocate(networkCapacity);
            application = ByteBuffer.allocate(applicationCapacity);
        }
    }
}
