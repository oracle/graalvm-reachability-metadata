/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_alpn_server;

import java.nio.ByteBuffer;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import org.eclipse.jetty.alpn.server.ALPNServerConnection;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.NegotiatingServerConnection.CipherDiscriminator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Jetty_alpn_serverTest {
    private static final String HTTP_2 = "h2";
    private static final String HTTP_1_1 = "http/1.1";

    @Test
    void serverPreferenceDeterminesSelectedProtocol() {
        TestFixture fixture = new TestFixture(new TestConnectionFactory(HTTP_2), new TestConnectionFactory(HTTP_1_1));
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2, HTTP_1_1), HTTP_1_1);

        connection.select(List.of(HTTP_1_1, HTTP_2));

        assertThat(connection.getProtocol()).isEqualTo(HTTP_2);
        assertThat(connection.getProtocols()).containsExactly(HTTP_2, HTTP_1_1);
        assertThat(connection.getDefaultProtocol()).isEqualTo(HTTP_1_1);
        assertThat(connection.getConnector()).isSameAs(fixture.connector);
        assertThat(connection.getSSLEngine()).isSameAs(fixture.sslEngine);
    }

    @Test
    void cipherDiscriminatorCanRejectAnOtherwiseMatchingProtocol() {
        DiscriminatingConnectionFactory rejectedHttp2 = new DiscriminatingConnectionFactory(HTTP_2, false);
        DiscriminatingConnectionFactory acceptedHttp11 = new DiscriminatingConnectionFactory(HTTP_1_1, true);
        TestFixture fixture = new TestFixture(rejectedHttp2, acceptedHttp11);
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2, HTTP_1_1), HTTP_1_1);

        connection.select(List.of(HTTP_2, HTTP_1_1));

        assertThat(connection.getProtocol()).isEqualTo(HTTP_1_1);
        assertThat(rejectedHttp2.checkedProtocols).containsExactly(HTTP_2);
        assertThat(rejectedHttp2.checkedTlsProtocols).containsExactly("TLSv1.3");
        assertThat(rejectedHttp2.checkedCipherSuites).containsExactly("TLS_AES_128_GCM_SHA256");
        assertThat(acceptedHttp11.checkedProtocols).containsExactly(HTTP_1_1);
    }

    @Test
    void unsupportedAlpnFallsBackToDefaultProtocol() {
        TestFixture fixture = new TestFixture(new TestConnectionFactory(HTTP_2), new TestConnectionFactory(HTTP_1_1));
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2, HTTP_1_1), HTTP_1_1);

        connection.unsupported();

        assertThat(connection.getProtocol()).isEqualTo(HTTP_1_1);
    }

    @Test
    void cipherDiscriminatorUsesSslSessionWhenHandshakeSessionIsUnavailable() {
        DiscriminatingConnectionFactory acceptedHttp2 = new DiscriminatingConnectionFactory(HTTP_2, true);
        TestSslSession sslSession = new TestSslSession("TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
        TestFixture fixture = new TestFixture(sslSession, null, acceptedHttp2, new TestConnectionFactory(HTTP_1_1));
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2, HTTP_1_1), HTTP_1_1);

        connection.select(List.of(HTTP_2));

        assertThat(connection.getProtocol()).isEqualTo(HTTP_2);
        assertThat(acceptedHttp2.checkedProtocols).containsExactly(HTTP_2);
        assertThat(acceptedHttp2.checkedTlsProtocols).containsExactly("TLSv1.2");
        assertThat(acceptedHttp2.checkedCipherSuites).containsExactly("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    }

    @Test
    void selectFailsWhenClientAndServerHaveNoProtocolInCommon() {
        TestFixture fixture = new TestFixture(new TestConnectionFactory(HTTP_2), new TestConnectionFactory(HTTP_1_1));
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2, HTTP_1_1), HTTP_1_1);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> connection.select(List.of("acme-tls/1")));
        assertThat(connection.getProtocol()).isNull();
    }

    @Test
    void selectedProtocolUpgradesEndpointToMatchingConnectionFactory() {
        TestConnectionFactory http2Factory = new TestConnectionFactory(HTTP_2);
        TestFixture fixture = new TestFixture(http2Factory, new TestConnectionFactory(HTTP_1_1));
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2, HTTP_1_1), HTTP_1_1);
        fixture.endPoint.setConnection(connection);

        connection.select(List.of(HTTP_2));
        connection.onFillable();

        assertThat(fixture.endPoint.getConnection()).isInstanceOf(RecordingConnection.class);
        assertThat(http2Factory.createdConnections).hasSize(1);
        assertThat(http2Factory.createdConnections.get(0).opened).isTrue();
    }

    @Test
    void selectedProtocolWithoutConnectionFactoryClosesEndpoint() {
        TestConnectionFactory http11Factory = new TestConnectionFactory(HTTP_1_1);
        TestFixture fixture = new TestFixture(http11Factory);
        ALPNServerConnection connection = fixture.newAlpnConnection(List.of(HTTP_2), HTTP_1_1);
        fixture.endPoint.setConnection(connection);

        connection.select(List.of(HTTP_2));
        connection.onFillable();

        assertThat(connection.getProtocol()).isEqualTo(HTTP_2);
        assertThat(fixture.endPoint.isOpen()).isFalse();
        assertThat(http11Factory.createdConnections).isEmpty();
    }

    @Test
    void serverConnectionFactoryReportsUnavailableProcessorWhenNoProviderIsPresent() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new ALPNServerConnectionFactory(" h2, http/1.1 "))
                .withMessage("No Server ALPNProcessors!");
    }

    private static final class TestFixture {
        private final ByteArrayEndPoint endPoint;
        private final ServerConnector connector;
        private final TestSslEngine sslEngine;

        private TestFixture(TestConnectionFactory... factories) {
            this(
                    new TestSslSession("TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"),
                    new TestSslSession("TLSv1.3", "TLS_AES_128_GCM_SHA256"),
                    factories);
        }

        private TestFixture(SSLSession session, SSLSession handshakeSession, TestConnectionFactory... factories) {
            Server server = new Server();
            this.connector = new ServerConnector(server, factories);
            this.endPoint = new ByteArrayEndPoint();
            this.sslEngine = new TestSslEngine(session, handshakeSession);
        }

        private ALPNServerConnection newAlpnConnection(List<String> protocols, String defaultProtocol) {
            return new ALPNServerConnection(connector, endPoint, sslEngine, protocols, defaultProtocol);
        }
    }

    private static class TestConnectionFactory extends AbstractConnectionFactory {
        private final List<RecordingConnection> createdConnections = new ArrayList<>();

        private TestConnectionFactory(String protocol) {
            super(protocol);
        }

        @Override
        public Connection newConnection(Connector connector, EndPoint endPoint) {
            RecordingConnection connection = new RecordingConnection(endPoint, connector.getExecutor());
            createdConnections.add(connection);
            return connection;
        }
    }

    private static final class DiscriminatingConnectionFactory extends TestConnectionFactory implements CipherDiscriminator {
        private final boolean acceptable;
        private final List<String> checkedProtocols = new ArrayList<>();
        private final List<String> checkedTlsProtocols = new ArrayList<>();
        private final List<String> checkedCipherSuites = new ArrayList<>();

        private DiscriminatingConnectionFactory(String protocol, boolean acceptable) {
            super(protocol);
            this.acceptable = acceptable;
        }

        @Override
        public boolean isAcceptable(String protocol, String tlsProtocol, String tlsCipher) {
            checkedProtocols.add(protocol);
            checkedTlsProtocols.add(tlsProtocol);
            checkedCipherSuites.add(tlsCipher);
            return acceptable;
        }
    }

    private static final class RecordingConnection extends AbstractConnection {
        private boolean opened;

        private RecordingConnection(EndPoint endPoint, Executor executor) {
            super(endPoint, executor);
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

    private static final class TestSslEngine extends SSLEngine {
        private final SSLSession session;
        private final SSLSession handshakeSession;
        private boolean outboundClosed;
        private boolean inboundClosed;

        private TestSslEngine(SSLSession session, SSLSession handshakeSession) {
            this.session = session;
            this.handshakeSession = handshakeSession;
        }

        @Override
        public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) throws SSLException {
            return result();
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
            return result();
        }

        @Override
        public Runnable getDelegatedTask() {
            return null;
        }

        @Override
        public void closeInbound() throws SSLException {
            inboundClosed = true;
        }

        @Override
        public boolean isInboundDone() {
            return inboundClosed;
        }

        @Override
        public void closeOutbound() {
            outboundClosed = true;
        }

        @Override
        public boolean isOutboundDone() {
            return outboundClosed;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return getEnabledCipherSuites();
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return new String[] {"TLS_AES_128_GCM_SHA256"};
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getSupportedProtocols() {
            return getEnabledProtocols();
        }

        @Override
        public String[] getEnabledProtocols() {
            return new String[] {"TLSv1.3"};
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
        }

        @Override
        public SSLSession getSession() {
            return session;
        }

        @Override
        public SSLSession getHandshakeSession() {
            return handshakeSession;
        }

        @Override
        public void beginHandshake() throws SSLException {
        }

        @Override
        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }

        @Override
        public void setUseClientMode(boolean mode) {
        }

        @Override
        public boolean getUseClientMode() {
            return false;
        }

        @Override
        public void setNeedClientAuth(boolean need) {
        }

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(boolean want) {
        }

        @Override
        public boolean getWantClientAuth() {
            return false;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
        }

        @Override
        public boolean getEnableSessionCreation() {
            return true;
        }

        private SSLEngineResult result() {
            return new SSLEngineResult(
                    SSLEngineResult.Status.OK,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                    0,
                    0);
        }
    }

    private static final class TestSslSession implements SSLSession {
        private final String protocol;
        private final String cipherSuite;

        private TestSslSession(String protocol, String cipherSuite) {
            this.protocol = protocol;
            this.cipherSuite = cipherSuite;
        }

        @Override
        public byte[] getId() {
            return new byte[] {1, 2, 3};
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public long getCreationTime() {
            return 0L;
        }

        @Override
        public long getLastAccessedTime() {
            return 0L;
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("peer is not verified");
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("peer is not verified");
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return cipherSuite;
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public String getPeerHost() {
            return "localhost";
        }

        @Override
        public int getPeerPort() {
            return 443;
        }

        @Override
        public int getPacketBufferSize() {
            return 16 * 1024;
        }

        @Override
        public int getApplicationBufferSize() {
            return 16 * 1024;
        }

        @Override
        public String toString() {
            return "TestSslSession" + Arrays.asList(protocol, cipherSuite);
        }
    }
}
