/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class DuckTypedPSKKeyManagerTest {
    private static final String PROTOCOL = "TLSv1.2";
    private static final String IDENTITY_HINT = "server-hint";
    private static final String IDENTITY = "client-identity";
    private static final SecretKey PSK = new SecretKeySpec(
            new byte[] {0x10, 0x21, 0x32, 0x43, 0x54, 0x65, 0x76, 0x01,
                    0x12, 0x23, 0x34, 0x45, 0x56, 0x67, 0x02, 0x13},
            "RAW");

    @Test
    void engineHandshakeUsesDuckTypedPskKeyManager() throws Exception {
        Provider provider = Conscrypt.newProvider();
        DuckTypedKeyManager keyManager = new DuckTypedKeyManager();
        SSLContext context = newPskContext(provider, keyManager);
        String cipherSuite = pskCipherSuite(context);
        SSLEngine client = context.createSSLEngine("localhost", 443);
        SSLEngine server = context.createSSLEngine();
        configure(client, true, cipherSuite);
        configure(server, false, cipherSuite);

        handshake(client, server);

        assertThat(client.getSession().getCipherSuite()).isEqualTo(cipherSuite);
        assertThat(server.getSession().getCipherSuite()).isEqualTo(cipherSuite);
        assertThat(keyManager.engineServerIdentityHints.get()).isGreaterThanOrEqualTo(1);
        assertThat(keyManager.engineClientIdentities.get()).isGreaterThanOrEqualTo(1);
        assertThat(keyManager.engineKeys.get()).isGreaterThanOrEqualTo(2);
    }

    private static SSLContext newPskContext(Provider provider, KeyManager keyManager)
            throws Exception {
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(new KeyManager[] {keyManager}, new TrustManager[0], new SecureRandom());
        assertThat(Conscrypt.isConscrypt(context)).isTrue();
        return context;
    }

    private static String pskCipherSuite(SSLContext context) {
        String[] supportedCipherSuites = context.createSSLEngine().getSupportedCipherSuites();
        String cipherSuite = Arrays.stream(supportedCipherSuites)
                .filter("TLS_PSK_WITH_AES_128_CBC_SHA"::equals)
                .findFirst()
                .orElse(null);
        assertThat(cipherSuite).isNotNull();
        return cipherSuite;
    }

    private static void configure(SSLEngine engine, boolean clientMode, String cipherSuite) {
        engine.setUseClientMode(clientMode);
        engine.setEnabledProtocols(new String[] {PROTOCOL});
        engine.setEnabledCipherSuites(new String[] {cipherSuite});
    }

    private static void handshake(SSLEngine client, SSLEngine server) throws Exception {
        client.beginHandshake();
        server.beginHandshake();
        ByteBuffer clientToServer = emptyPacketBuffer(client);
        ByteBuffer serverToClient = emptyPacketBuffer(server);
        ByteBuffer emptyApplicationData = ByteBuffer.allocate(0);
        ByteBuffer clientApplicationData = applicationBuffer(client);
        ByteBuffer serverApplicationData = applicationBuffer(server);

        for (int i = 0; i < 1000; i++) {
            boolean clientComplete = complete(client);
            boolean serverComplete = complete(server);
            if (clientComplete && serverComplete) {
                return;
            }
            boolean progressed = false;
            progressed |= handshakeStep(client, emptyApplicationData, serverToClient,
                    clientToServer, clientApplicationData);
            progressed |= handshakeStep(server, emptyApplicationData, clientToServer,
                    serverToClient, serverApplicationData);
            if (!progressed && !complete(client) && !complete(server)) {
                throw new SSLException("SSLEngine PSK handshake made no progress");
            }
        }
        throw new SSLException("SSLEngine PSK handshake did not finish");
    }

    private static ByteBuffer emptyPacketBuffer(SSLEngine engine) {
        ByteBuffer buffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize() * 2);
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer applicationBuffer(SSLEngine engine) {
        return ByteBuffer.allocate(engine.getSession().getApplicationBufferSize() * 2);
    }

    private static boolean handshakeStep(SSLEngine engine, ByteBuffer sourceApplicationData,
            ByteBuffer inboundPacketData, ByteBuffer outboundPacketData,
            ByteBuffer inboundApplicationData) throws SSLException {
        switch (engine.getHandshakeStatus()) {
            case NEED_TASK:
                runTasks(engine);
                return true;
            case NEED_WRAP:
                outboundPacketData.compact();
                SSLEngineResult wrapResult = engine.wrap(sourceApplicationData, outboundPacketData);
                outboundPacketData.flip();
                runTasks(engine);
                assertThat(wrapResult.getStatus()).isNotEqualTo(SSLEngineResult.Status.CLOSED);
                return wrapResult.bytesProduced() > 0 || wrapResult.getHandshakeStatus()
                        == SSLEngineResult.HandshakeStatus.FINISHED;
            case NEED_UNWRAP:
                SSLEngineResult unwrapResult = engine.unwrap(inboundPacketData,
                        inboundApplicationData);
                runTasks(engine);
                assertThat(unwrapResult.getStatus()).isNotEqualTo(SSLEngineResult.Status.CLOSED);
                return unwrapResult.bytesConsumed() > 0 || unwrapResult.getHandshakeStatus()
                        == SSLEngineResult.HandshakeStatus.FINISHED;
            case FINISHED:
            case NOT_HANDSHAKING:
                return false;
            default:
                throw new SSLException(
                        "Unexpected handshake status: " + engine.getHandshakeStatus());
        }
    }

    private static void runTasks(SSLEngine engine) {
        Runnable task = engine.getDelegatedTask();
        while (task != null) {
            task.run();
            task = engine.getDelegatedTask();
        }
    }

    private static boolean complete(SSLEngine engine) {
        SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
        return status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                || status == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    public static final class DuckTypedKeyManager implements KeyManager {
        private final AtomicInteger engineServerIdentityHints = new AtomicInteger();
        private final AtomicInteger engineClientIdentities = new AtomicInteger();
        private final AtomicInteger engineKeys = new AtomicInteger();

        public String chooseServerKeyIdentityHint(Socket socket) {
            return IDENTITY_HINT;
        }

        public String chooseServerKeyIdentityHint(SSLEngine engine) {
            engineServerIdentityHints.incrementAndGet();
            return IDENTITY_HINT;
        }

        public String chooseClientKeyIdentity(String identityHint, Socket socket) {
            return IDENTITY;
        }

        public String chooseClientKeyIdentity(String identityHint, SSLEngine engine) {
            engineClientIdentities.incrementAndGet();
            return IDENTITY;
        }

        public SecretKey getKey(String identityHint, String identity, Socket socket) {
            assertThat(identity).isEqualTo(IDENTITY);
            return PSK;
        }

        public SecretKey getKey(String identityHint, String identity, SSLEngine engine) {
            engineKeys.incrementAndGet();
            assertThat(identity).isEqualTo(IDENTITY);
            return PSK;
        }
    }
}
