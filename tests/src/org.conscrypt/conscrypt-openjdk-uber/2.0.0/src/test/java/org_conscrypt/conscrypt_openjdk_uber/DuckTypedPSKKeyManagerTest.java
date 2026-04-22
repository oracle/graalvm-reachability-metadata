/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

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
