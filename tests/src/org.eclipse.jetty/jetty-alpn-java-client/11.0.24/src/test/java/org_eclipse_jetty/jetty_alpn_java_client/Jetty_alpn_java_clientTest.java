/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_alpn_java_client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.eclipse.jetty.alpn.client.ALPNClientConnection;
import org.eclipse.jetty.alpn.client.ALPNClientConnectionFactory;
import org.eclipse.jetty.alpn.java.client.JDK9ClientALPNProcessor;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.ALPNProcessor;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Jetty_alpn_java_clientTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final List<String> PROTOCOLS = List.of("h2", "http/1.1");

    @Test
    void initAcceptsCurrentJdkAndProcessorAppliesOnlyToJdkSslEngines() throws Exception {
        JDK9ClientALPNProcessor processor = new JDK9ClientALPNProcessor();
        SSLEngine jdkSslEngine = newClientEngine();

        assertThatCode(processor::init).doesNotThrowAnyException();
        assertThat(processor.appliesTo(jdkSslEngine)).isTrue();
        assertThat(processor.appliesTo(new TestSslEngine("h2"))).isFalse();
        assertThat(jdkSslEngine.getSSLParameters().getApplicationProtocols()).isEmpty();
    }

    @Test
    void clientAlpnProcessorIsAvailableThroughServiceLoader() {
        ServiceLoader<ALPNProcessor.Client> processors = ServiceLoader.load(ALPNProcessor.Client.class);

        assertThat(processors)
                .anySatisfy(processor -> assertThat(processor).isInstanceOf(JDK9ClientALPNProcessor.class));
    }

    @Test
    void configurePublishesClientProtocolPreferenceOnSslEngine() {
        JDK9ClientALPNProcessor processor = new JDK9ClientALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new TestSslEngine(null));

        processor.configure(context.sslEngine, context.alpnConnection);

        assertThat(context.sslEngine.getSSLParameters().getApplicationProtocols()).containsExactly("h2", "http/1.1");
        assertThat(context.sslConnection.handshakeListeners).hasSize(1);
        assertThat(context.alpnConnection.selectedProtocols).isEmpty();
    }

    @Test
    void handshakeSucceededRecordsNegotiatedApplicationProtocol() throws Exception {
        JDK9ClientALPNProcessor processor = new JDK9ClientALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new TestSslEngine("h2"));
        processor.configure(context.sslEngine, context.alpnConnection);

        context.sslConnection.handshakeListener().handshakeSucceeded(new SslHandshakeListener.Event(context.sslEngine));

        assertThat(context.alpnConnection.selectedProtocols).containsExactly("h2");
    }

    @Test
    void handshakeSucceededRecordsNullWhenNoProtocolWasNegotiated() throws Exception {
        JDK9ClientALPNProcessor processor = new JDK9ClientALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new TestSslEngine(""));
        processor.configure(context.sslEngine, context.alpnConnection);

        context.sslConnection.handshakeListener().handshakeSucceeded(new SslHandshakeListener.Event(context.sslEngine));

        assertThat(context.alpnConnection.selectedProtocols).containsExactly((String) null);
    }

    @Test
    void clientConnectionFactoryCreatesAlpnConnectionConfiguredByLoadedProcessor() throws Exception {
        ALPNClientConnectionFactory factory = new ALPNClientConnectionFactory(
                DIRECT_EXECUTOR,
                new TestClientConnectionFactory(),
                PROTOCOLS);
        SSLEngine sslEngine = newClientEngine();
        SslConnection sslConnection = new SslConnection(
                new ArrayByteBufferPool(),
                DIRECT_EXECUTOR,
                new ByteArrayEndPoint(),
                sslEngine);
        Map<String, Object> context = new HashMap<>();
        context.put(SslClientConnectionFactory.SSL_ENGINE_CONTEXT_KEY, sslEngine);

        Connection connection = factory.newConnection(sslConnection.getDecryptedEndPoint(), context);

        assertThat(connection).isInstanceOf(ALPNClientConnection.class);
        ALPNClientConnection alpnConnection = (ALPNClientConnection) connection;
        assertThat(alpnConnection.getProtocols()).containsExactly("h2", "http/1.1");
        assertThat(alpnConnection.getSSLEngine()).isSameAs(sslEngine);
        assertThat(sslEngine.getSSLParameters().getApplicationProtocols()).containsExactly("h2", "http/1.1");
    }

    @Test
    void clientConnectionFactoryRejectsSslEnginesThatNoLoadedProcessorAppliesTo() {
        ALPNClientConnectionFactory factory = new ALPNClientConnectionFactory(
                DIRECT_EXECUTOR,
                new TestClientConnectionFactory(),
                PROTOCOLS);
        Map<String, Object> context = new HashMap<>();
        TestSslEngine sslEngine = new TestSslEngine(null);
        context.put(SslClientConnectionFactory.SSL_ENGINE_CONTEXT_KEY, sslEngine);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> factory.newConnection(new ByteArrayEndPoint(), context))
                .withMessage("No ALPNProcessor for " + sslEngine);
    }

    private static ALPNTestContext newALPNTestContext(TestSslEngine sslEngine) {
        RecordingSslConnection sslConnection = new RecordingSslConnection(sslEngine);
        RecordingALPNClientConnection alpnConnection = new RecordingALPNClientConnection(
                sslConnection.getDecryptedEndPoint(),
                sslEngine,
                new HashMap<>());
        return new ALPNTestContext(sslEngine, sslConnection, alpnConnection);
    }

    private static SSLEngine newClientEngine() throws Exception {
        SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine("localhost", 443);
        sslEngine.setUseClientMode(true);
        return sslEngine;
    }

    private static final class ALPNTestContext {
        private final TestSslEngine sslEngine;
        private final RecordingSslConnection sslConnection;
        private final RecordingALPNClientConnection alpnConnection;

        private ALPNTestContext(
                TestSslEngine sslEngine,
                RecordingSslConnection sslConnection,
                RecordingALPNClientConnection alpnConnection) {
            this.sslEngine = sslEngine;
            this.sslConnection = sslConnection;
            this.alpnConnection = alpnConnection;
        }
    }

    private static final class RecordingSslConnection extends SslConnection {
        private final List<SslHandshakeListener> handshakeListeners = new ArrayList<>();

        private RecordingSslConnection(SSLEngine sslEngine) {
            super(new ArrayByteBufferPool(), DIRECT_EXECUTOR, new ByteArrayEndPoint(), sslEngine);
        }

        @Override
        public void addHandshakeListener(SslHandshakeListener listener) {
            super.addHandshakeListener(listener);
            handshakeListeners.add(listener);
        }

        private SslHandshakeListener handshakeListener() {
            assertThat(handshakeListeners).hasSize(1);
            return handshakeListeners.get(0);
        }
    }

    private static final class RecordingALPNClientConnection extends ALPNClientConnection {
        private final List<String> selectedProtocols = new ArrayList<>();

        private RecordingALPNClientConnection(
                EndPoint endPoint,
                SSLEngine sslEngine,
                Map<String, Object> context) {
            super(endPoint, DIRECT_EXECUTOR, new TestClientConnectionFactory(), sslEngine, context, PROTOCOLS);
        }

        @Override
        public void selected(String protocol) {
            selectedProtocols.add(protocol);
        }
    }

    private static final class TestClientConnectionFactory implements ClientConnectionFactory {
        @Override
        public Connection newConnection(EndPoint endPoint, Map<String, Object> context) {
            return new RecordingConnection(endPoint);
        }
    }

    private static final class RecordingConnection extends AbstractConnection {
        private RecordingConnection(EndPoint endPoint) {
            super(endPoint, DIRECT_EXECUTOR);
        }

        @Override
        public void onFillable() {
        }
    }

    private static final class TestSslEngine extends SSLEngine {
        private final String applicationProtocol;
        private SSLParameters sslParameters = new SSLParameters();
        private BiFunction<SSLEngine, List<String>, String> handshakeApplicationProtocolSelector;
        private boolean useClientMode;
        private boolean needClientAuth;
        private boolean wantClientAuth;
        private boolean enableSessionCreation = true;
        private boolean inboundClosed;
        private boolean outboundClosed;

        private TestSslEngine(String applicationProtocol) {
            this.applicationProtocol = applicationProtocol;
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
            return null;
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
            useClientMode = mode;
        }

        @Override
        public boolean getUseClientMode() {
            return useClientMode;
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            needClientAuth = need;
        }

        @Override
        public boolean getNeedClientAuth() {
            return needClientAuth;
        }

        @Override
        public void setWantClientAuth(boolean want) {
            wantClientAuth = want;
        }

        @Override
        public boolean getWantClientAuth() {
            return wantClientAuth;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            enableSessionCreation = flag;
        }

        @Override
        public boolean getEnableSessionCreation() {
            return enableSessionCreation;
        }

        @Override
        public SSLParameters getSSLParameters() {
            return sslParameters;
        }

        @Override
        public void setSSLParameters(SSLParameters sslParameters) {
            this.sslParameters = sslParameters;
        }

        @Override
        public String getApplicationProtocol() {
            return applicationProtocol;
        }

        @Override
        public String getHandshakeApplicationProtocol() {
            return applicationProtocol;
        }

        @Override
        public void setHandshakeApplicationProtocolSelector(
                BiFunction<SSLEngine, List<String>, String> selector) {
            handshakeApplicationProtocolSelector = selector;
        }

        @Override
        public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
            return handshakeApplicationProtocolSelector;
        }

        private SSLEngineResult result() {
            return new SSLEngineResult(
                    SSLEngineResult.Status.OK,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                    0,
                    0);
        }
    }
}
