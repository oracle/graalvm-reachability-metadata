/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_alpn_java_server;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.alpn.java.server.JDK9ServerALPNProcessor;
import org.eclipse.jetty.alpn.server.ALPNServerConnection;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.ALPNProcessor;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NegotiatingServerConnection.CipherDiscriminator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class Jetty_alpn_java_serverTest {
    private static final List<String> SERVER_PROTOCOLS = List.of("h2", "http/1.1");
    private static final String DEFAULT_PROTOCOL = "http/1.1";

    @Test
    void initAcceptsCurrentJdkAndProcessorAppliesToJdkSslEngine() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        SSLEngine sslEngine = newServerEngine();

        assertThatCode(processor::init).doesNotThrowAnyException();
        assertThat(processor.appliesTo(sslEngine)).isTrue();
        assertThat(sslEngine.getHandshakeApplicationProtocolSelector()).isNull();
    }

    @Test
    void serverAlpnProcessorIsAvailableThroughServiceLoader() {
        ServiceLoader<ALPNProcessor.Server> processors = ServiceLoader.load(ALPNProcessor.Server.class);

        assertThat(processors)
                .anySatisfy(processor -> assertThat(processor).isInstanceOf(JDK9ServerALPNProcessor.class));
    }

    @Test
    void configureInstallsApplicationProtocolSelectorOnSslEngine() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new HttpConnectionFactory());

        processor.configure(context.sslEngine, context.alpnConnection);

        BiFunction<SSLEngine, List<String>, String> selector =
                context.sslEngine.getHandshakeApplicationProtocolSelector();
        assertThat(selector).isNotNull();
        assertThat(context.alpnConnection.getProtocol()).isNull();
    }

    @Test
    void selectorChoosesFirstServerPreferredProtocolOfferedByClient() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new HttpConnectionFactory());
        processor.configure(context.sslEngine, context.alpnConnection);

        String selectedProtocol = select(context, List.of("http/1.1", "h2"));

        assertThat(selectedProtocol).isEqualTo("h2");
        assertThat(context.alpnConnection.getProtocol()).isEqualTo("h2");
    }

    @Test
    void selectorUsesDefaultProtocolWhenClientDoesNotAdvertiseAlpnProtocols() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new HttpConnectionFactory());
        processor.configure(context.sslEngine, context.alpnConnection);

        String selectedProtocol = select(context, List.of());

        assertThat(selectedProtocol).isEqualTo(DEFAULT_PROTOCOL);
        assertThat(context.alpnConnection.getProtocol()).isEqualTo(DEFAULT_PROTOCOL);
    }

    @Test
    void selectorReturnsNullWhenThereIsNoCommonProtocol() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new HttpConnectionFactory());
        processor.configure(context.sslEngine, context.alpnConnection);

        String selectedProtocol = select(context, List.of("acme-tls/1"));

        assertThat(selectedProtocol).isNull();
        assertThat(context.alpnConnection.getProtocol()).isNull();
    }

    @Test
    void selectorHonorsConnectorCipherDiscriminatorForCandidateProtocols() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        RejectingConnectionFactory rejectingH2Factory = new RejectingConnectionFactory("h2");
        ALPNTestContext context = newALPNTestContext(rejectingH2Factory, new HttpConnectionFactory());
        processor.configure(context.sslEngine, context.alpnConnection);

        String selectedProtocol = select(context, List.of("h2", "http/1.1"));

        assertThat(selectedProtocol).isEqualTo("http/1.1");
        assertThat(context.alpnConnection.getProtocol()).isEqualTo("http/1.1");
        assertThat(rejectingH2Factory.seenProtocol).isEqualTo("h2");
        assertThat(rejectingH2Factory.seenTlsProtocol).isNotBlank();
        assertThat(rejectingH2Factory.seenCipherSuite).isNotBlank();
    }

    @Test
    void handshakeSucceededFallsBackToDefaultProtocolWhenNoProtocolWasNegotiated() throws Exception {
        JDK9ServerALPNProcessor processor = new JDK9ServerALPNProcessor();
        ALPNTestContext context = newALPNTestContext(new HttpConnectionFactory());
        processor.configure(context.sslEngine, context.alpnConnection);

        SslHandshakeListener listener = handshakeListener(context);
        listener.handshakeSucceeded(new SslHandshakeListener.Event(context.sslEngine));

        assertThat(context.alpnConnection.getProtocol()).isEqualTo(DEFAULT_PROTOCOL);
    }

    private static String select(ALPNTestContext context, List<String> clientProtocols) {
        BiFunction<SSLEngine, List<String>, String> selector =
                context.sslEngine.getHandshakeApplicationProtocolSelector();
        assertThat(selector).isNotNull();
        return selector.apply(context.sslEngine, clientProtocols);
    }

    private static SslHandshakeListener handshakeListener(ALPNTestContext context) {
        BiFunction<SSLEngine, List<String>, String> selector =
                context.sslEngine.getHandshakeApplicationProtocolSelector();
        assertThat(selector).isInstanceOf(SslHandshakeListener.class);
        return (SslHandshakeListener) selector;
    }

    private static ALPNTestContext newALPNTestContext(ConnectionFactory... connectionFactories) throws Exception {
        ServerConnector connector = new ServerConnector(new Server(), connectionFactories);
        SSLEngine sslEngine = newServerEngine();
        ByteArrayEndPoint encryptedEndPoint = new ByteArrayEndPoint();
        SslConnection sslConnection = new SslConnection(
                connector.getByteBufferPool(),
                connector.getExecutor(),
                encryptedEndPoint,
                sslEngine);
        encryptedEndPoint.setConnection(sslConnection);
        EndPoint decryptedEndPoint = sslConnection.getDecryptedEndPoint();
        ALPNServerConnection alpnConnection = new ALPNServerConnection(
                connector,
                decryptedEndPoint,
                sslEngine,
                SERVER_PROTOCOLS,
                DEFAULT_PROTOCOL);
        decryptedEndPoint.setConnection(alpnConnection);
        return new ALPNTestContext(sslEngine, alpnConnection);
    }

    private static SSLEngine newServerEngine() throws Exception {
        SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine();
        sslEngine.setUseClientMode(false);
        return sslEngine;
    }

    private static final class ALPNTestContext {
        private final SSLEngine sslEngine;
        private final ALPNServerConnection alpnConnection;

        private ALPNTestContext(SSLEngine sslEngine, ALPNServerConnection alpnConnection) {
            this.sslEngine = sslEngine;
            this.alpnConnection = alpnConnection;
        }
    }

    private static final class RejectingConnectionFactory extends AbstractConnectionFactory
            implements CipherDiscriminator {
        private String seenProtocol;
        private String seenTlsProtocol;
        private String seenCipherSuite;

        private RejectingConnectionFactory(String protocol) {
            super(protocol);
        }

        @Override
        public boolean isAcceptable(String protocol, String tlsProtocol, String cipherSuite) {
            seenProtocol = protocol;
            seenTlsProtocol = tlsProtocol;
            seenCipherSuite = cipherSuite;
            return false;
        }

        @Override
        public org.eclipse.jetty.io.Connection newConnection(Connector connector, EndPoint endPoint) {
            throw new UnsupportedOperationException("Test connection factory does not create connections");
        }
    }
}
