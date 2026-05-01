/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_alpn_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadPendingException;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.alpn.client.ALPNClientConnection;
import org.eclipse.jetty.alpn.client.ALPNClientConnectionFactory;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

public class Jetty_alpn_clientTest {
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @Test
    void clientConnectionExposesNegotiationInputs() throws Exception {
        RecordingEndPoint endPoint = new RecordingEndPoint();
        RecordingClientConnectionFactory connectionFactory = new RecordingClientConnectionFactory(
                new StubConnection(endPoint));
        SSLEngine sslEngine = newSslEngine();
        Map<String, Object> context = new HashMap<>();
        List<String> protocols = new ArrayList<>(List.of("h2", "http/1.1"));

        ALPNClientConnection connection = new ALPNClientConnection(
                endPoint,
                DIRECT_EXECUTOR,
                connectionFactory,
                sslEngine,
                context,
                protocols);

        assertThat(connection.getEndPoint()).isSameAs(endPoint);
        assertThat(connection.getSSLEngine()).isSameAs(sslEngine);
        assertThat(connection.getProtocol()).isNull();
        assertThat(connection.getProtocols()).containsExactly("h2", "http/1.1");
    }

    @Test
    void selectedProtocolCompletesNegotiationAndUpgradesOnOpen() throws Exception {
        RecordingEndPoint endPoint = new RecordingEndPoint();
        StubConnection negotiatedConnection = new StubConnection(endPoint);
        RecordingClientConnectionFactory connectionFactory = new RecordingClientConnectionFactory(negotiatedConnection);
        Map<String, Object> context = new HashMap<>();
        context.put("application", "client");
        ALPNClientConnection connection = newConnection(
                endPoint,
                connectionFactory,
                context,
                List.of("h2", "http/1.1"));

        connection.selected("h2");
        connection.onOpen();

        assertThat(connection.getProtocol()).isEqualTo("h2");
        assertThat(endPoint.flushCalls).isEqualTo(1);
        assertThat(endPoint.fillInterestedCallbacks).isEmpty();
        assertThat(connectionFactory.newConnectionCalls).isEqualTo(1);
        assertThat(connectionFactory.lastEndPoint).isSameAs(endPoint);
        assertThat(connectionFactory.lastContext).isSameAs(context);
        assertThat(endPoint.upgradedConnection).isSameAs(negotiatedConnection);
    }

    @Test
    void onOpenWaitsForNetworkReadWhenProtocolHasNotBeenSelected() throws Exception {
        RecordingEndPoint endPoint = new RecordingEndPoint();
        RecordingClientConnectionFactory connectionFactory = new RecordingClientConnectionFactory(
                new StubConnection(endPoint));
        ALPNClientConnection connection = newConnection(endPoint, connectionFactory, new HashMap<>(), List.of("h2"));

        connection.onOpen();

        assertThat(connection.getProtocol()).isNull();
        assertThat(endPoint.flushCalls).isEqualTo(1);
        assertThat(endPoint.fillInterestedCallbacks).hasSize(1);
        assertThat(connectionFactory.newConnectionCalls).isZero();
        assertThat(endPoint.upgradedConnection).isNull();
    }

    @Test
    void onFillableUpgradesAfterProtocolSelection() throws Exception {
        RecordingEndPoint endPoint = new RecordingEndPoint();
        StubConnection negotiatedConnection = new StubConnection(endPoint);
        RecordingClientConnectionFactory connectionFactory = new RecordingClientConnectionFactory(negotiatedConnection);
        ALPNClientConnection connection = newConnection(endPoint, connectionFactory, new HashMap<>(), List.of("h2"));

        connection.selected("h2");
        connection.onFillable();

        assertThat(endPoint.fillCalls).isEqualTo(1);
        assertThat(connectionFactory.newConnectionCalls).isEqualTo(1);
        assertThat(endPoint.upgradedConnection).isSameAs(negotiatedConnection);
    }

    @Test
    void closeShutsDownEndpointOutput() throws Exception {
        RecordingEndPoint endPoint = new RecordingEndPoint();
        ALPNClientConnection connection = newConnection(
                endPoint,
                new RecordingClientConnectionFactory(new StubConnection(endPoint)),
                new HashMap<>(),
                List.of("h2"));

        connection.close();

        assertThat(endPoint.outputShutdown).isTrue();
        assertThat(endPoint.open).isFalse();
    }

    @Test
    void factoryRejectsEmptyProtocolListBeforeLoadingProcessors() {
        RecordingClientConnectionFactory delegate = new RecordingClientConnectionFactory(
                new StubConnection(new RecordingEndPoint()));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ALPNClientConnectionFactory(DIRECT_EXECUTOR, delegate, List.of()))
                .withMessage("ALPN protocol list cannot be empty");
    }

    @Test
    void factoryReportsMissingClientAlpnProcessors() {
        RecordingClientConnectionFactory delegate = new RecordingClientConnectionFactory(
                new StubConnection(new RecordingEndPoint()));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new ALPNClientConnectionFactory(DIRECT_EXECUTOR, delegate, List.of("h2")))
                .withMessage("No Client ALPNProcessors!");
    }

    private static ALPNClientConnection newConnection(
            RecordingEndPoint endPoint,
            ClientConnectionFactory connectionFactory,
            Map<String, Object> context,
            List<String> protocols) throws Exception {
        return new ALPNClientConnection(
                endPoint,
                DIRECT_EXECUTOR,
                connectionFactory,
                newSslEngine(),
                context,
                protocols);
    }

    private static SSLEngine newSslEngine() throws Exception {
        return SSLContext.getDefault().createSSLEngine("localhost", 443);
    }

    private static final class RecordingClientConnectionFactory implements ClientConnectionFactory {
        private final Connection connection;
        private int newConnectionCalls;
        private EndPoint lastEndPoint;
        private Map<String, Object> lastContext;

        private RecordingClientConnectionFactory(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection newConnection(EndPoint endPoint, Map<String, Object> context) {
            newConnectionCalls++;
            lastEndPoint = endPoint;
            lastContext = context;
            return connection;
        }
    }

    private static final class RecordingEndPoint implements EndPoint {
        private final InetSocketAddress localAddress = InetSocketAddress.createUnresolved("localhost", 8443);
        private final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved("example.com", 443);
        private final long createdTimeStamp = System.currentTimeMillis();
        private final List<Callback> fillInterestedCallbacks = new ArrayList<>();
        private boolean open = true;
        private boolean outputShutdown;
        private boolean inputShutdown;
        private boolean fillInterested;
        private long idleTimeout;
        private Connection connection;
        private Connection upgradedConnection;
        private int fillCalls;
        private int flushCalls;

        @Override
        public InetSocketAddress getLocalAddress() {
            return localAddress;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public long getCreatedTimeStamp() {
            return createdTimeStamp;
        }

        @Override
        public void shutdownOutput() {
            outputShutdown = true;
            open = false;
        }

        @Override
        public boolean isOutputShutdown() {
            return outputShutdown;
        }

        @Override
        public boolean isInputShutdown() {
            return inputShutdown;
        }

        @Override
        public void close(Throwable cause) {
            inputShutdown = true;
            outputShutdown = true;
            open = false;
        }

        @Override
        public int fill(ByteBuffer buffer) throws IOException {
            fillCalls++;
            return 0;
        }

        @Override
        public boolean flush(ByteBuffer... buffers) throws IOException {
            flushCalls++;
            return true;
        }

        @Override
        public Object getTransport() {
            return this;
        }

        @Override
        public long getIdleTimeout() {
            return idleTimeout;
        }

        @Override
        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        @Override
        public void fillInterested(Callback callback) throws ReadPendingException {
            if (fillInterested) {
                throw new ReadPendingException();
            }
            fillInterested = true;
            fillInterestedCallbacks.add(callback);
        }

        @Override
        public boolean tryFillInterested(Callback callback) {
            if (fillInterested) {
                return false;
            }
            fillInterested(callback);
            return true;
        }

        @Override
        public boolean isFillInterested() {
            return fillInterested;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void onOpen() {
            open = true;
        }

        @Override
        public void onClose(Throwable cause) {
            open = false;
        }

        @Override
        public void upgrade(Connection connection) {
            upgradedConnection = connection;
            setConnection(connection);
        }
    }

    private static final class StubConnection implements Connection {
        private final EndPoint endPoint;
        private final long createdTimeStamp = System.currentTimeMillis();

        private StubConnection(EndPoint endPoint) {
            this.endPoint = endPoint;
        }

        @Override
        public void addEventListener(EventListener listener) {
        }

        @Override
        public void removeEventListener(EventListener listener) {
        }

        @Override
        public void onOpen() {
        }

        @Override
        public void onClose(Throwable cause) {
        }

        @Override
        public EndPoint getEndPoint() {
            return endPoint;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean onIdleExpired() {
            return false;
        }

        @Override
        public long getMessagesIn() {
            return 0;
        }

        @Override
        public long getMessagesOut() {
            return 0;
        }

        @Override
        public long getBytesIn() {
            return 0;
        }

        @Override
        public long getBytesOut() {
            return 0;
        }

        @Override
        public long getCreatedTimeStamp() {
            return createdTimeStamp;
        }
    }
}
