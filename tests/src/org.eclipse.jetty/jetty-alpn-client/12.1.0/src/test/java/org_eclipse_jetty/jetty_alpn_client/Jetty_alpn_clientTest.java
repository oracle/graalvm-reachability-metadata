/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_alpn_client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.alpn.client.ALPNClientConnection;
import org.eclipse.jetty.alpn.client.ALPNClientConnectionFactory;
import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Promise;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Jetty_alpn_clientTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;
    private static final List<String> PROTOCOLS = List.of("h2", "http/1.1");

    @Test
    void clientConnectionExposesSslEngineAndConfiguredProtocols() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        SSLEngine sslEngine = newClientEngine();
        Map<String, Object> context = new HashMap<>();
        ALPNClientConnection connection = new ALPNClientConnection(
                endPoint,
                DIRECT_EXECUTOR,
                connectionFactory,
                sslEngine,
                context,
                PROTOCOLS);

        assertThat(connection.getSSLEngine()).isSameAs(sslEngine);
        assertThat(connection.getProtocols()).containsExactly("h2", "http/1.1");
        assertThat(connection.getProtocol()).isNull();
        assertThat(connectionFactory.createdConnections).isEmpty();
    }

    @Test
    void selectedProtocolIsRecordedWithoutImmediatelyReplacingTheConnection() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        ALPNClientConnection connection = newAlpnConnection(endPoint, connectionFactory, new HashMap<>());
        endPoint.setConnection(connection);

        connection.selected("h2");

        assertThat(connection.getProtocol()).isEqualTo("h2");
        assertThat(endPoint.getConnection()).isSameAs(connection);
        assertThat(connectionFactory.createdConnections).isEmpty();
    }

    @Test
    void selectedProtocolBeforeOpenUpgradesEndPointToProtocolConnection() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        Map<String, Object> context = new HashMap<>();
        context.put("test.name", "selectedProtocolBeforeOpenUpgradesEndPointToProtocolConnection");
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        ALPNClientConnection connection = newAlpnConnection(endPoint, connectionFactory, context);
        endPoint.setConnection(connection);

        connection.selected("http/1.1");
        connection.onOpen();

        assertThat(connection.getProtocol()).isEqualTo("http/1.1");
        assertThat(connectionFactory.createdConnections).hasSize(1);
        assertThat(connectionFactory.seenEndPoints).containsExactly(endPoint);
        assertThat(connectionFactory.seenContexts).containsExactly(context);
        assertThat(endPoint.getConnection()).isSameAs(connectionFactory.createdConnections.get(0));
        assertThat(connectionFactory.createdConnections.get(0).opened).isTrue();
    }

    @Test
    void selectedProtocolOnFillableUpgradesEndPointToProtocolConnection() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        Map<String, Object> context = new HashMap<>();
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        ALPNClientConnection connection = newAlpnConnection(endPoint, connectionFactory, context);
        endPoint.setConnection(connection);

        connection.selected("h2");
        connection.onFillable();

        assertThat(connection.getProtocol()).isEqualTo("h2");
        assertThat(connectionFactory.createdConnections).hasSize(1);
        assertThat(connectionFactory.seenEndPoints).containsExactly(endPoint);
        assertThat(connectionFactory.seenContexts).containsExactly(context);
        assertThat(endPoint.getConnection()).isSameAs(connectionFactory.createdConnections.get(0));
        assertThat(connectionFactory.createdConnections.get(0).opened).isTrue();
    }

    @Test
    void onFillableKeepsWaitingWhenNegotiationHasNotCompletedAndNoBytesAreAvailable() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        ALPNClientConnection connection = newAlpnConnection(endPoint, connectionFactory, new HashMap<>());
        endPoint.setConnection(connection);

        connection.onFillable();

        assertThat(connection.getProtocol()).isNull();
        assertThat(endPoint.getConnection()).isSameAs(connection);
        assertThat(endPoint.isFillInterested()).isTrue();
        assertThat(connectionFactory.createdConnections).isEmpty();
    }

    @Test
    void endOfInputUpgradesEndPointWithoutNegotiatedProtocol() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        RecordingPromise connectionPromise = new RecordingPromise();
        Map<String, Object> context = newContextWithConnectionPromise(connectionPromise);
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        ALPNClientConnection connection = newAlpnConnection(endPoint, connectionFactory, context);
        endPoint.setConnection(connection);
        endPoint.addInputEOF();

        connection.onFillable();

        assertThat(connection.getProtocol()).isNull();
        assertThat(connectionFactory.createdConnections).hasSize(1);
        RecordingConnection protocolConnection = connectionFactory.createdConnections.get(0);
        assertThat(endPoint.getConnection()).isSameAs(protocolConnection);
        assertThat(protocolConnection.opened).isTrue();
        assertThat(connectionPromise.failure).isNull();
        assertThat(endPoint.isInputShutdown()).isTrue();
        assertThat(endPoint.isOpen()).isTrue();
    }

    @Test
    void closeShutsDownEndPointOutput() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        TestClientConnectionFactory connectionFactory = new TestClientConnectionFactory();
        ALPNClientConnection connection = newAlpnConnection(endPoint, connectionFactory, new HashMap<>());
        endPoint.setConnection(connection);

        connection.close();

        assertThat(endPoint.isOutputShutdown()).isTrue();
        assertThat(endPoint.isOpen()).isFalse();
    }

    @Test
    void failedProtocolConnectionCreationClosesEndPoint() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        RecordingPromise connectionPromise = new RecordingPromise();
        Map<String, Object> context = newContextWithConnectionPromise(connectionPromise);
        ClientConnectionFactory failingConnectionFactory = (ignoredEndPoint, ignoredContext) -> {
            throw new IOException("Unable to create protocol connection");
        };
        ALPNClientConnection connection = newAlpnConnection(endPoint, failingConnectionFactory, context);
        endPoint.setConnection(connection);

        connection.selected("h2");
        connection.onOpen();

        assertThat(connection.getProtocol()).isEqualTo("h2");
        assertThat(connectionPromise.failure).isNull();
        assertThat(endPoint.getConnection()).isSameAs(connection);
        assertThat(endPoint.isOutputShutdown()).isTrue();
        assertThat(endPoint.isOpen()).isFalse();
    }

    @Test
    void clientConnectionFactoryRejectsEmptyProtocolListBeforeLookingUpProcessors() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ALPNClientConnectionFactory(
                        DIRECT_EXECUTOR,
                        new TestClientConnectionFactory(),
                        List.of()))
                .withMessage("ALPN protocol list cannot be empty");
    }

    @Test
    void clientConnectionFactoryReportsMissingProcessorWhenNoClientAlpnProcessorServiceIsPresent() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new ALPNClientConnectionFactory(
                        DIRECT_EXECUTOR,
                        new TestClientConnectionFactory(),
                        PROTOCOLS))
                .withMessage("No Client ALPNProcessors!");
    }

    private static ALPNClientConnection newAlpnConnection(
            EndPoint endPoint,
            ClientConnectionFactory connectionFactory,
            Map<String, Object> context) throws Exception {
        return new ALPNClientConnection(
                endPoint,
                DIRECT_EXECUTOR,
                connectionFactory,
                newClientEngine(),
                context,
                PROTOCOLS);
    }

    private static SSLEngine newClientEngine() throws Exception {
        SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine("localhost", 443);
        sslEngine.setUseClientMode(true);
        return sslEngine;
    }

    private static Map<String, Object> newContextWithConnectionPromise(RecordingPromise connectionPromise) {
        Map<String, Object> context = new HashMap<>();
        context.put(ClientConnector.CONNECTION_PROMISE_CONTEXT_KEY, connectionPromise);
        return context;
    }

    private static final class RecordingPromise implements Promise<Connection> {
        private Throwable failure;

        @Override
        public void failed(Throwable failure) {
            this.failure = failure;
        }
    }

    private static final class TestClientConnectionFactory implements ClientConnectionFactory {
        private final List<EndPoint> seenEndPoints = new ArrayList<>();
        private final List<Map<String, Object>> seenContexts = new ArrayList<>();
        private final List<RecordingConnection> createdConnections = new ArrayList<>();

        @Override
        public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException {
            seenEndPoints.add(endPoint);
            seenContexts.add(context);
            RecordingConnection connection = new RecordingConnection(endPoint);
            createdConnections.add(connection);
            return connection;
        }
    }

    private static final class RecordingConnection extends AbstractConnection {
        private boolean opened;

        private RecordingConnection(EndPoint endPoint) {
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
