/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.conscrypt.ApplicationProtocolSelector;
import org.conscrypt.BufferAllocator;
import org.conscrypt.Conscrypt;
import org.conscrypt.HandshakeListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Conscrypt_openjdk_uberTest {

    private static final char[] KEY_PASSWORD = "changeit".toCharArray();

    private static final String CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDjzCCAnegAwIBAgIUO5oHmmf+5qVbly17/26muNYUnzswDQYJKoZIhvcNAQEL
            BQAwVjELMAkGA1UEBhMCWFgxFTATBgNVBAcMDERlZmF1bHQgQ2l0eTEcMBoGA1UE
            CgwTRGVmYXVsdCBDb21wYW55IEx0ZDESMBAGA1UEAwwJbG9jYWxob3N0MCAXDTIy
            MDQyMTA3NDgwNFoYDzMwMjEwODIyMDc0ODA0WjBWMQswCQYDVQQGEwJYWDEVMBMG
            A1UEBwwMRGVmYXVsdCBDaXR5MRwwGgYDVQQKDBNEZWZhdWx0IENvbXBhbnkgTHRk
            MRIwEAYDVQQDDAlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
            AoIBAQDAjRNfsO1LHH/1gZ9sx3YpEXRkA4EHQoUHC4yOn6qYLpR+Rg+rs69D/xUa
            7tHUNx0QVLMFPfpKNarNq1ftMlf5pyHQhME40+9fotKSh2o4QCiZmJwdQ54ZiDhk
            iIgSFrl/MJVcgyjNjg62/rnvDWbvy4FZvPxMKd2c1A4K/79/gY7N8utar1AKWDJJ
            esAB+09+8Z7utUaKBGSXoTXYe+JkZW2/AEeiJRP8PxUnyXJEzEm0vvh/NxHycCS/
            qswOIssbshyDDVw0PKJuEd3oGLn7fK7EFMc9/vR/H7RrSb08qaaLKghIvlwgm3ju
            mV4A7cr6bSQ6THKHibheeWfP7W57AgMBAAGjUzBRMB0GA1UdDgQWBBQ5oh+xJQMV
            d1Z5eukXsVTPmvmhuzAfBgNVHSMEGDAWgBQ5oh+xJQMVd1Z5eukXsVTPmvmhuzAP
            BgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCHe/kqkToP6F48hfzn
            IpnQ8b6WSoFBeaxddK3EAF53p5WMG0siovn/dulpDmeR9zeajKRZoU/zgHck6tZM
            FgiCo7cD3YNjHvgAoCPSWZpU/cPy22fOTS66ht/LUEWzpeih4Zm3vRjUIgcaA93e
            RcUDXiyIgP7LOKNwe3R/EDHGmolMC7ZRLWne9P1O3fYcsvVR/aSUjwH0XiLj7D2P
            qFr1IxZ1LyAD0jtJ3+FCxDHsaxBO0nTUlKghGeEcl4woqR2fe9IPTxE3Zh7iRMxw
            pBdV7EvXeoeBENgavRrYjt+AIwl4FN/HGjW5hdZkP6ZYlMcFSa9WMpQZVfhQzMib
            WPBD
            -----END CERTIFICATE-----
            """;

    private static final String PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDAjRNfsO1LHH/1
            gZ9sx3YpEXRkA4EHQoUHC4yOn6qYLpR+Rg+rs69D/xUa7tHUNx0QVLMFPfpKNarN
            q1ftMlf5pyHQhME40+9fotKSh2o4QCiZmJwdQ54ZiDhkiIgSFrl/MJVcgyjNjg62
            /rnvDWbvy4FZvPxMKd2c1A4K/79/gY7N8utar1AKWDJJesAB+09+8Z7utUaKBGSX
            oTXYe+JkZW2/AEeiJRP8PxUnyXJEzEm0vvh/NxHycCS/qswOIssbshyDDVw0PKJu
            Ed3oGLn7fK7EFMc9/vR/H7RrSb08qaaLKghIvlwgm3jumV4A7cr6bSQ6THKHibhe
            eWfP7W57AgMBAAECggEAUXyioVGH6syh3m0/4dI70D+ByT2uYjslfwjFMCqkEIlN
            8G6H/TsqxhTygpZlGHFGjH270VEcVkGGCkokCM7QamfvqIY2G22dlRScFsTWD/4e
            8HmkP8I460c5zln1c+pIEGqoGyGqp+shkRIV7/P1BzBRin+vKjBhiBg55S+8hCG7
            ht5Tpdwr7eTswJhEnIe7By98QesPjHim4Iv2LWoNxGckOxK7rQfTXItEZ8EyiWHk
            KuE8342fQXePynAkNYxiqF+skDbDMOaAjE26HrPjSvJcMrh/v3KERALJ/5O/sMTX
            FYAYH0UQsiqgQ5eTzJ/u277/xVDRbEjKYsFxe6oHgQKBgQD97saspYY7xEnHB8ku
            sFQr/72d+Jf/V88JxIVb8CF8gKQQcqjpUXP9+AMkQ9Bovpcdz8daUS0iftwlAiMW
            vKb5/n/awAqhv+f9xRJ4vuLewfXDYVyekNAjnzGTppxwZX4kxbQqUgFr/jYtt+Rs
            QatVWggFcXde3THbwFi+6Q3nQQKBgQDCHl+bOGpwtrGomIgC5gs8NPpuSh/1tPY3
            hsbCqZhB2cc/Wnkbb2x5ZRS7Q0TqhhorAqqVtVvA9UOqgjCPUgN2UZMg6ZcGxd+J
            C44qCZjJLARnJMJ+DnUpetvcf4uD3cPAXhSTKKc/UXeARFdfwD2QISOsWsjGfZo2
            LGT6yDcCuwKBgQCV6/TGl8+B1+kLJlCkTRzVAV/NNalf61LkXG+0ETlkDXtP7rJF
            Vn7aEiSgs50HSodj5Xm4nDa+qBGHlBRtZyJadOS9nSZpyyjIDiNSwLindfKI0SHi
            yHLqpSGbIAI65eEtCsDNP69xOBx6r5igRcFHWilkNVKZ4pR+PrjmtigsQQKBgEHY
            N6nZCxHMhpqfkpMZIPp9Je0/K3QWu+W9y7HAAStlCZbNw4Kw2uSaituyR7AdaMbq
            Ep7Rc5wSFa/ClWIn+ZCqvvNNCLN9bwN3bfOIadDjI2MxLt+/W7KEQAudH9/M21dn
            EieQhJqPsa9KfzC1bqxy8TzHVo8tj5+Mk3wVacN9AoGAK8bEoH7umJ9zhvZ63nUA
            /Sz5iPYwXVjJGfq6AsGkNJ1ss1315iTt2Z1axKk3yKT17P251e/strBn6smsvi3f
            RMnlGcPg9sk6Y8Cx6tQ7e4XosVcKN+Q6ussfLcscDvl7Fj6OuKXyaGUSjDrijr3z
            9Mbnz8b5HgISbAM3W3nz+6w=
            -----END PRIVATE KEY-----
            """;

    @Test
    void integratesWithProviderServices() throws Exception {
        Provider provider = Conscrypt.newProviderBuilder()
                .setName("ConscryptTestProvider")
                .provideTrustManager()
                .build();
        byte[] message = "conscrypt-provider".getBytes(StandardCharsets.UTF_8);

        assertThat(Conscrypt.isAvailable()).isTrue();
        assertThat(Conscrypt.isConscrypt(provider)).isTrue();
        assertThat(provider.getName()).isEqualTo("ConscryptTestProvider");
        assertThat(Conscrypt.version().major()).isGreaterThan(0);
        assertThat(Conscrypt.maxEncryptedPacketLength()).isGreaterThan(0);

        MessageDigest digest = MessageDigest.getInstance("SHA-256", provider);
        byte[] digestBytes = digest.digest(message);

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", provider);
        keyGenerator.init(128);
        SecretKey encryptionKey = keyGenerator.generateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        byte[] initializationVector = new byte[12];
        new SecureRandom().nextBytes(initializationVector);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, initializationVector));
        byte[] ciphertext = cipher.doFinal(message);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, initializationVector));
        byte[] plaintext = cipher.doFinal(ciphertext);

        Mac mac = Mac.getInstance("HmacSHA256", provider);
        mac.init(new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] macBytes = mac.doFinal(message);

        SSLContext context = SSLContext.getInstance("TLS", provider);
        X509TrustManager defaultTrustManager = Conscrypt.getDefaultX509TrustManager();

        assertThat(digestBytes).hasSize(32);
        assertThat(plaintext).containsExactly(message);
        assertThat(macBytes).hasSize(32);
        assertThat(Conscrypt.isConscrypt(context)).isTrue();
        assertThat(defaultTrustManager.getAcceptedIssuers()).isNotNull();
    }

    @Test
    void completesMutualTlsHandshakeOverEngines() throws Exception {
        Provider provider = Conscrypt.newProvider();
        TlsContexts tlsContexts = createMutualTlsContexts(provider);

        SSLEngine clientEngine = tlsContexts.clientContext().createSSLEngine("localhost", 443);
        SSLEngine serverEngine = tlsContexts.serverContext().createSSLEngine();
        clientEngine.setUseClientMode(true);
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        Conscrypt.setHostname(clientEngine, "localhost");

        completeHandshake(clientEngine, serverEngine);

        assertThat(serverEngine.getSession().getPeerCertificates()).isNotEmpty();
        assertThat(clientEngine.getSession().getPeerCertificates()).isNotEmpty();

        clientEngine.closeOutbound();
        serverEngine.closeOutbound();
    }

    @Test
    void negotiatesTlsOverEnginesAndExchangesApplicationData() throws Exception {
        Provider provider = Conscrypt.newProvider();
        TlsContexts tlsContexts = createTlsContexts(provider);
        AtomicInteger clientHandshakeCount = new AtomicInteger();
        AtomicInteger serverHandshakeCount = new AtomicInteger();
        BufferAllocator allocator = BufferAllocator.unpooled();

        Conscrypt.setDefaultBufferAllocator(allocator);

        SSLEngine clientEngine = tlsContexts.clientContext().createSSLEngine("localhost", 443);
        SSLEngine serverEngine = tlsContexts.serverContext().createSSLEngine();
        clientEngine.setUseClientMode(true);
        serverEngine.setUseClientMode(false);

        assertThat(Conscrypt.isConscrypt(clientEngine)).isTrue();
        assertThat(Conscrypt.isConscrypt(serverEngine)).isTrue();

        Conscrypt.setBufferAllocator(clientEngine, allocator);
        Conscrypt.setBufferAllocator(serverEngine, allocator);
        Conscrypt.setHostname(clientEngine, "localhost");
        Conscrypt.setUseSessionTickets(clientEngine, true);
        Conscrypt.setUseSessionTickets(serverEngine, true);
        Conscrypt.setApplicationProtocols(clientEngine, new String[] {"http/1.1", "h2"});
        Conscrypt.setApplicationProtocols(serverEngine, new String[] {"h2", "http/1.1"});
        Conscrypt.setApplicationProtocolSelector(serverEngine, new FixedProtocolSelector("h2"));
        Conscrypt.setHandshakeListener(clientEngine, new CountingHandshakeListener(clientHandshakeCount));
        Conscrypt.setHandshakeListener(serverEngine, new CountingHandshakeListener(serverHandshakeCount));

        assertThat(Conscrypt.getHostname(clientEngine)).isEqualTo("localhost");
        assertThat(Conscrypt.getApplicationProtocols(clientEngine)).containsExactly("http/1.1", "h2");
        assertThat(Conscrypt.maxSealOverhead(clientEngine)).isGreaterThanOrEqualTo(0);

        completeHandshake(clientEngine, serverEngine);

        assertThat(clientHandshakeCount.get()).isEqualTo(1);
        assertThat(serverHandshakeCount.get()).isEqualTo(1);
        assertThat(Conscrypt.getApplicationProtocol(clientEngine)).isEqualTo("h2");
        assertThat(Conscrypt.getApplicationProtocol(serverEngine)).isEqualTo("h2");

        byte[] clientMessage = "engine-client-payload".getBytes(StandardCharsets.UTF_8);
        byte[] serverMessage = "engine-server-payload".getBytes(StandardCharsets.UTF_8);
        byte[] receivedByServer = transferApplicationData(clientEngine, serverEngine, clientMessage, true);
        byte[] receivedByClient = transferApplicationData(serverEngine, clientEngine, serverMessage, false);
        byte[] exportedByClient = Conscrypt.exportKeyingMaterial(clientEngine, "conscrypt-test", null, 24);
        byte[] exportedByServer = Conscrypt.exportKeyingMaterial(serverEngine, "conscrypt-test", null, 24);

        assertThat(receivedByServer).containsExactly(clientMessage);
        assertThat(receivedByClient).containsExactly(serverMessage);
        assertThat(exportedByClient).hasSize(24);
        assertThat(exportedByClient).containsExactly(exportedByServer);

        clientEngine.closeOutbound();
        serverEngine.closeOutbound();
    }

    @Test
    void exposesTlsUniqueAfterTls12EngineHandshake() throws Exception {
        Provider provider = Conscrypt.newProvider();
        TlsContexts tlsContexts = createTlsContexts(provider);

        SSLEngine clientEngine = tlsContexts.clientContext().createSSLEngine("localhost", 443);
        SSLEngine serverEngine = tlsContexts.serverContext().createSSLEngine();
        clientEngine.setUseClientMode(true);
        serverEngine.setUseClientMode(false);
        clientEngine.setEnabledProtocols(new String[] {"TLSv1.2"});
        serverEngine.setEnabledProtocols(new String[] {"TLSv1.2"});

        Conscrypt.setHostname(clientEngine, "localhost");

        completeHandshake(clientEngine, serverEngine);

        byte[] clientTlsUnique = Conscrypt.getTlsUnique(clientEngine);
        byte[] serverTlsUnique = Conscrypt.getTlsUnique(serverEngine);

        assertThat(clientTlsUnique).isNotNull().isNotEmpty();
        assertThat(serverTlsUnique).containsExactly(clientTlsUnique);

        clientEngine.closeOutbound();
        serverEngine.closeOutbound();
    }

    private static TlsContexts createTlsContexts(Provider provider) throws Exception {
        X509Certificate certificate = parseCertificate(CERTIFICATE_PEM);
        PrivateKey privateKey = parsePrivateKey(PRIVATE_KEY_PEM);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", privateKey, KEY_PASSWORD, new X509Certificate[] {certificate});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEY_PASSWORD);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext serverContext = SSLContext.getInstance("TLS", provider);
        serverContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

        SSLContext clientContext = SSLContext.getInstance("TLS", provider);
        clientContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

        return new TlsContexts(serverContext, clientContext);
    }

    private static TlsContexts createMutualTlsContexts(Provider provider) throws Exception {
        X509Certificate certificate = parseCertificate(CERTIFICATE_PEM);
        PrivateKey privateKey = parsePrivateKey(PRIVATE_KEY_PEM);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("shared", privateKey, KEY_PASSWORD, new X509Certificate[] {certificate});

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEY_PASSWORD);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("shared", certificate);

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext serverContext = SSLContext.getInstance("TLS", provider);
        serverContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

        SSLContext clientContext = SSLContext.getInstance("TLS", provider);
        clientContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), new SecureRandom());

        return new TlsContexts(serverContext, clientContext);
    }

    private static X509Certificate parseCertificate(String pem) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII))) {
            return (X509Certificate) certificateFactory.generateCertificate(inputStream);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
        String normalizedPem = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(normalizedPem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private static void completeHandshake(SSLEngine firstEngine, SSLEngine secondEngine) throws SSLException {
        ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
        int packetBufferSize = Math.max(firstEngine.getSession().getPacketBufferSize(), secondEngine.getSession().getPacketBufferSize()) * 4;
        int applicationBufferSize = Math.max(firstEngine.getSession().getApplicationBufferSize(), secondEngine.getSession().getApplicationBufferSize()) * 4;
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
            progressed |= unwrapHandshakeData(secondEngine, firstToSecond, secondApplicationBuffer, true);
            progressed |= unwrapHandshakeData(firstEngine, secondToFirst, firstApplicationBuffer, false);

            if (!progressed) {
                throw new SSLException("Handshake did not make progress");
            }
        }

        assertThat(handshakeComplete(firstEngine, secondEngine)).isTrue();
    }

    private static boolean handshakeComplete(SSLEngine firstEngine, SSLEngine secondEngine) {
        return handshakeFinished(firstEngine.getHandshakeStatus()) && handshakeFinished(secondEngine.getHandshakeStatus());
    }

    private static boolean handshakeFinished(HandshakeStatus handshakeStatus) {
        return handshakeStatus == HandshakeStatus.FINISHED || handshakeStatus == HandshakeStatus.NOT_HANDSHAKING;
    }

    private static boolean runDelegatedTasks(SSLEngine engine) {
        boolean ranTask = false;
        while (engine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable delegatedTask = engine.getDelegatedTask();
            if (delegatedTask == null) {
                break;
            }
            delegatedTask.run();
            ranTask = true;
        }
        return ranTask;
    }

    private static boolean wrapHandshakeData(SSLEngine engine, ByteBuffer networkBuffer, ByteBuffer sourceBuffer) throws SSLException {
        if (engine.getHandshakeStatus() != HandshakeStatus.NEED_WRAP) {
            return false;
        }
        SSLEngineResult result = engine.wrap(sourceBuffer.duplicate(), networkBuffer);
        assertThat(result.getStatus()).isIn(SSLEngineResult.Status.OK, SSLEngineResult.Status.CLOSED);
        return result.bytesProduced() > 0 || result.getHandshakeStatus() != HandshakeStatus.NEED_WRAP;
    }

    private static boolean unwrapHandshakeData(
            SSLEngine engine,
            ByteBuffer networkBuffer,
            ByteBuffer applicationBuffer,
            boolean useArrayUnwrap) throws SSLException {
        if (networkBuffer.position() == 0) {
            return false;
        }
        networkBuffer.flip();
        SSLEngineResult result = useArrayUnwrap
                ? Conscrypt.unwrap(engine, new ByteBuffer[] {networkBuffer}, new ByteBuffer[] {applicationBuffer})
                : Conscrypt.unwrap(engine, new ByteBuffer[] {networkBuffer}, 0, 1, new ByteBuffer[] {applicationBuffer}, 0, 1);
        networkBuffer.compact();
        assertThat(result.getStatus()).isIn(
                SSLEngineResult.Status.OK,
                SSLEngineResult.Status.BUFFER_UNDERFLOW,
                SSLEngineResult.Status.CLOSED);
        if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            throw new SSLException("Application buffer overflow during handshake");
        }
        return result.bytesConsumed() > 0 || result.getHandshakeStatus() == HandshakeStatus.FINISHED;
    }

    private static byte[] transferApplicationData(
            SSLEngine sourceEngine,
            SSLEngine targetEngine,
            byte[] message,
            boolean useArrayUnwrap) throws SSLException {
        ByteBuffer sourceBuffer = ByteBuffer.wrap(message);
        ByteBuffer networkBuffer = ByteBuffer.allocate(sourceEngine.getSession().getPacketBufferSize() + message.length + 256);

        while (sourceBuffer.hasRemaining()) {
            SSLEngineResult result = sourceEngine.wrap(sourceBuffer, networkBuffer);
            assertThat(result.getStatus()).isEqualTo(SSLEngineResult.Status.OK);
        }

        networkBuffer.flip();
        ByteBuffer targetBuffer = ByteBuffer.allocate(targetEngine.getSession().getApplicationBufferSize() + message.length + 256);
        while (networkBuffer.hasRemaining()) {
            SSLEngineResult result = useArrayUnwrap
                    ? Conscrypt.unwrap(targetEngine, new ByteBuffer[] {networkBuffer}, new ByteBuffer[] {targetBuffer})
                    : Conscrypt.unwrap(targetEngine, new ByteBuffer[] {networkBuffer}, 0, 1, new ByteBuffer[] {targetBuffer}, 0, 1);
            assertThat(result.getStatus()).isEqualTo(SSLEngineResult.Status.OK);
        }

        targetBuffer.flip();
        byte[] received = new byte[targetBuffer.remaining()];
        targetBuffer.get(received);
        return received;
    }

    private record TlsContexts(SSLContext serverContext, SSLContext clientContext) {
    }

    private static final class FixedProtocolSelector extends ApplicationProtocolSelector {

        private final String protocol;

        private FixedProtocolSelector(String protocol) {
            this.protocol = protocol;
        }

        @Override
        public String selectApplicationProtocol(SSLEngine engine, List<String> protocols) {
            return protocols.contains(this.protocol) ? this.protocol : null;
        }

        @Override
        public String selectApplicationProtocol(SSLSocket socket, List<String> protocols) {
            return protocols.contains(this.protocol) ? this.protocol : null;
        }
    }

    private static final class CountingHandshakeListener extends HandshakeListener {

        private final AtomicInteger handshakeCount;

        private CountingHandshakeListener(AtomicInteger handshakeCount) {
            this.handshakeCount = handshakeCount;
        }

        @Override
        public void onHandshakeFinished() {
            this.handshakeCount.incrementAndGet();
        }
    }
}
