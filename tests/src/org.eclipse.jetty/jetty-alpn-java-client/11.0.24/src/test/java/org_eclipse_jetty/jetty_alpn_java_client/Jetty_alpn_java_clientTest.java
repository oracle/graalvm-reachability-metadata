/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_alpn_java_client;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.jetty.alpn.client.ALPNClientConnection;
import org.eclipse.jetty.alpn.client.ALPNClientConnectionFactory;
import org.eclipse.jetty.alpn.java.client.JDK9ClientALPNProcessor;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.util.BufferUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
public class Jetty_alpn_java_clientTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final List<String> CLIENT_PROTOCOLS = List.of("http/1.1", "h2");

    @Test
    void processorSupportsTheJdkClientEngine() throws Exception {
        JDK9ClientALPNProcessor processor = new JDK9ClientALPNProcessor();
        SSLEngine engine = SSLContext.getDefault().createSSLEngine("localhost", 443);
        engine.setUseClientMode(true);

        processor.init();

        assertThat(processor.appliesTo(engine)).isTrue();
    }

    @Test
    void configureAdvertisesProtocolsInOrderAndPreservesSslParameters() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine("localhost", 443);
        engine.setUseClientMode(true);
        SSLParameters parameters = engine.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(parameters);
        String[] cipherSuites = engine.getEnabledCipherSuites();
        ByteArrayEndPoint encrypted = new ByteArrayEndPoint();
        SslConnection ssl = newSslConnection(encrypted, engine);
        ALPNClientConnection alpn = new ALPNClientConnection(
                ssl.getDecryptedEndPoint(), DIRECT_EXECUTOR,
                (endPoint, context) -> new ApplicationConnection(endPoint),
                engine, new HashMap<>(), CLIENT_PROTOCOLS);
        ssl.getDecryptedEndPoint().setConnection(alpn);
        try {
            new JDK9ClientALPNProcessor().configure(engine, alpn);

            assertThat(engine.getSSLParameters().getApplicationProtocols()).containsExactly("http/1.1", "h2");
            assertThat(engine.getSSLParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
            assertThat(engine.getEnabledCipherSuites()).containsExactly(cipherSuites);
            assertThat(alpn.getProtocol()).isNull();
        } finally {
            encrypted.close();
        }
    }

    @Test
    void connectionFactoryDiscoversAndConfiguresTheJavaAlpnProcessor() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine("localhost", 443);
        engine.setUseClientMode(true);
        ByteArrayEndPoint encrypted = new ByteArrayEndPoint();
        SslConnection ssl = newSslConnection(encrypted, engine);
        try {
            ALPNClientConnection alpn = newAlpnConnection(ssl);

            assertThat(alpn.getSSLEngine()).isSameAs(engine);
            assertThat(alpn.getProtocols()).containsExactlyElementsOf(CLIENT_PROTOCOLS);
            assertThat(engine.getSSLParameters().getApplicationProtocols()).containsExactly("http/1.1", "h2");
        } finally {
            encrypted.close();
        }
    }

    @Test
    void successfulHandshakeRecordsTheServerPreferredProtocolAndUpgradesTheConnection() throws Exception {
        assertNegotiation(List.of("h2", "http/1.1"), "h2");
    }

    @Test
    void serverWithoutAlpnCompletesNegotiationAndUpgradesWithNoSelectedProtocol() throws Exception {
        assertNegotiation(List.of(), null);
    }

    private static void assertNegotiation(List<String> serverProtocols, String expectedProtocol) throws Exception {
        SSLContext context = newTlsContext();
        SSLEngine clientEngine = context.createSSLEngine("localhost", 443);
        clientEngine.setUseClientMode(true);
        SSLEngine serverEngine = context.createSSLEngine();
        serverEngine.setUseClientMode(false);
        SSLParameters parameters = serverEngine.getSSLParameters();
        parameters.setApplicationProtocols(serverProtocols.toArray(new String[0]));
        serverEngine.setSSLParameters(parameters);

        ByteArrayEndPoint clientEncrypted = new ByteArrayEndPoint();
        ByteArrayEndPoint serverEncrypted = new ByteArrayEndPoint();
        clientEncrypted.setGrowOutput(true);
        serverEncrypted.setGrowOutput(true);
        SslConnection clientSsl = newSslConnection(clientEncrypted, clientEngine);
        SslConnection serverSsl = newSslConnection(serverEncrypted, serverEngine);
        ALPNClientConnection alpn = newAlpnConnection(clientSsl);
        HandshakeObserver clientHandshake = new HandshakeObserver();
        HandshakeObserver serverHandshake = new HandshakeObserver();
        clientSsl.addHandshakeListener(clientHandshake);
        serverSsl.addHandshakeListener(serverHandshake);
        try {
            for (int round = 0; round < 100 && !(clientHandshake.succeeded && serverHandshake.succeeded); round++) {
                progressHandshake(clientSsl);
                serverEncrypted.addInput(clientEncrypted.takeOutput());
                progressHandshake(serverSsl);
                clientEncrypted.addInput(serverEncrypted.takeOutput());
            }

            assertThat(clientHandshake.succeeded).as("client TLS handshake completed").isTrue();
            assertThat(serverHandshake.succeeded).as("server TLS handshake completed").isTrue();
            assertThat(clientEngine.getApplicationProtocol()).isEqualTo(expectedProtocol == null ? "" : expectedProtocol);
            assertThat(alpn.getProtocol()).isEqualTo(expectedProtocol);

            alpn.onOpen();

            assertThat(clientSsl.getDecryptedEndPoint().getConnection()).isInstanceOf(ApplicationConnection.class);
            ApplicationConnection application = (ApplicationConnection) clientSsl.getDecryptedEndPoint().getConnection();
            assertThat(application.opened).isTrue();
        } finally {
            clientEncrypted.close();
            serverEncrypted.close();
        }
    }

    private static void progressHandshake(SslConnection ssl) throws Exception {
        EndPoint plain = ssl.getDecryptedEndPoint();
        plain.flush(BufferUtil.EMPTY_BUFFER);
        ByteBuffer input = BufferUtil.allocate(1024);
        assertThat(plain.fill(input)).isZero();
    }

    private static SslConnection newSslConnection(ByteArrayEndPoint encrypted, SSLEngine engine) {
        SslConnection ssl = new SslConnection(new ArrayByteBufferPool(), DIRECT_EXECUTOR, encrypted, engine);
        encrypted.setConnection(ssl);
        return ssl;
    }

    private static ALPNClientConnection newAlpnConnection(SslConnection ssl) throws Exception {
        Map<String, Object> context = new HashMap<>();
        context.put(SslClientConnectionFactory.SSL_ENGINE_CONTEXT_KEY, ssl.getSSLEngine());
        ALPNClientConnectionFactory factory = new ALPNClientConnectionFactory(
                DIRECT_EXECUTOR, (endPoint, ignored) -> new ApplicationConnection(endPoint), CLIENT_PROTOCOLS);
        ALPNClientConnection alpn = (ALPNClientConnection) factory.newConnection(ssl.getDecryptedEndPoint(), context);
        ssl.getDecryptedEndPoint().setConnection(alpn);
        return alpn;
    }

    private static SSLContext newTlsContext() throws Exception {
        char[] password = "test-password".toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Jetty_alpn_java_clientTest.class.getResourceAsStream("/localhost.p12")) {
            assertThat(input).isNotNull();
            store.load(input, password);
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    private static final class HandshakeObserver implements SslHandshakeListener {
        private boolean succeeded;

        @Override
        public void handshakeSucceeded(Event event) {
            succeeded = true;
        }
    }

    private static final class ApplicationConnection extends AbstractConnection {
        private boolean opened;

        private ApplicationConnection(EndPoint endPoint) {
            super(endPoint, DIRECT_EXECUTOR);
        }

        @Override
        public void onOpen() {
            super.onOpen();
            opened = true;
        }

        @Override
        public void onFillable() {
        }
    }
}
