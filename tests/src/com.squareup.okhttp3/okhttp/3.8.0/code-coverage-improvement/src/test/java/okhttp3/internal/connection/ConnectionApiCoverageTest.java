/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import okhttp3.Address;
import okhttp3.Authenticator;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.Protocol;
import okhttp3.Route;
import okhttp3.TlsVersion;
import org.junit.jupiter.api.Test;

public class ConnectionApiCoverageTest {
    @Test
    void routeSelectionAndDatabaseTrackAProxyRoute() throws Exception {
        okhttp3.internal.Internal.initializeInstanceForTests();
        Address address = address(80, null, Collections.singletonList(ConnectionSpec.CLEARTEXT));
        RouteDatabase database = new RouteDatabase();
        RouteSelector selector = new RouteSelector(address, database);
        assertThat(selector.hasNext()).isTrue();
        Route route = selector.next();
        assertThat(route.address()).isSameAs(address);
        assertThat(route.proxy()).isEqualTo(Proxy.NO_PROXY);
        assertThat(route.socketAddress().getPort()).isEqualTo(80);
        assertThat(route.requiresTunnel()).isFalse();
        assertThat(route.toString()).contains("example.com");
        assertThat(route).isEqualTo(new Route(address, Proxy.NO_PROXY, route.socketAddress()));
        database.failed(route);
        assertThat(database.shouldPostpone(route)).isTrue();
        database.connected(route);
        assertThat(database.shouldPostpone(route)).isFalse();
        assertThat(selector.hasNext()).isTrue();
    }

    @Test
    void failedPublicRouteSelectionEventuallyRetriesPostponedRoutes() throws Exception {
        okhttp3.internal.Internal.initializeInstanceForTests();
        Address address = address(80, null, Collections.singletonList(ConnectionSpec.CLEARTEXT));
        RouteDatabase database = new RouteDatabase();
        RouteSelector firstSelector = new RouteSelector(address, database);
        Route failed = firstSelector.next();
        firstSelector.connectFailed(failed, new java.io.IOException("coverage failure"));
        RouteSelector retrySelector = new RouteSelector(address, database);
        Route postponed = null;
        while (retrySelector.hasNext()) {
            Route candidate = retrySelector.next();
            if (candidate.equals(failed)) {
                postponed = candidate;
                break;
            }
        }
        assertThat(postponed).isEqualTo(failed);
    }

    @Test
    void connectionSpecSelectorConfiguresTlsAndHandlesFailures() throws Exception {
        okhttp3.internal.Internal.initializeInstanceForTests();
        ConnectionSpecSelector selector = new ConnectionSpecSelector(Collections.singletonList(
                new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2).build()));
        javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket)
                SSLContext.getDefault().getSocketFactory().createSocket();
        assertThat(selector.configureSecureSocket(socket).isTls()).isTrue();
        assertThat(selector.connectionFailed(new java.io.IOException("retry"))).isFalse();
        socket.close();
    }

    @Test
    void realConnectionExposesRouteStateBeforeNetworkConnect() throws Exception {
        okhttp3.internal.Internal.initializeInstanceForTests();
        Address address = address(80, null, Collections.singletonList(ConnectionSpec.CLEARTEXT));
        Route route = new Route(address, Proxy.NO_PROXY,
                new java.net.InetSocketAddress("localhost", 80));
        RealConnection connection = new RealConnection(new ConnectionPool(2, 1, TimeUnit.MINUTES), route);
        assertThat(connection.route()).isSameAs(route);
        assertThat(connection.supportsUrl(okhttp3.HttpUrl.parse("http://example.com/"))).isTrue();
        assertThat(connection.supportsUrl(okhttp3.HttpUrl.parse("https://example.com/"))).isFalse();
        assertThat(connection.isEligible(address, route)).isTrue();
        assertThat(connection.protocol()).isNull();
        assertThat(connection.handshake()).isNull();
        RealConnection fixture = RealConnection.testConnection(new ConnectionPool(), route,
                new java.net.Socket(), 7L);
        assertThat(fixture.route()).isSameAs(route);
        try {
            connection.isHealthy(false);
        } catch (NullPointerException expectedBeforeConnect) {
            assertThat(expectedBeforeConnect).isNotNull();
        }
        assertThat(connection.toString()).contains("example.com");
        connection.cancel();
        assertThat(connection.noNewStreams).isFalse();
    }

    @Test
    void realConnectionHelpersExposeHealthWebSocketAndPeerCallbacks() throws Exception {
        okhttp3.internal.Internal.initializeInstanceForTests();
        Address address = address(80, null, Collections.singletonList(ConnectionSpec.CLEARTEXT));
        Route route = new Route(address, Proxy.NO_PROXY,
                new java.net.InetSocketAddress("localhost", 80));
        ConnectionPool pool = new ConnectionPool();
        java.net.Socket socket = new java.net.Socket();
        RealConnection connection = RealConnection.testConnection(pool, route, socket, 42L);
        assertThat(connection.socket()).isSameAs(socket);
        assertThat(connection.idleAtNanos).isEqualTo(42L);
        assertThat(connection.isHealthy(false)).isTrue();
        socket.close();
        assertThat(connection.isHealthy(false)).isFalse();

        RealConnection websocketConnection = RealConnection.testConnection(pool, route,
                new java.net.Socket(), 0L);
        StreamAllocation allocation = new StreamAllocation(pool, address, "websocket");
        assertThat(websocketConnection.newWebSocketStreams(allocation).client).isTrue();

        okhttp3.internal.http2.Http2Connection http2 = new okhttp3.internal.http2.Http2Connection.Builder(
                true).socket(new java.net.Socket(), "localhost", new okio.Buffer(),
                new okio.Buffer()).build();
        connection.onSettings(http2);
        assertThat(connection.allocationLimit).isEqualTo(http2.maxConcurrentStreams());
        okhttp3.internal.http2.Http2Connection server =
                new okhttp3.internal.http2.Http2Connection.Builder(false).socket(new java.net.Socket(),
                        "localhost", new okio.Buffer(), new okio.Buffer()).build();
        okhttp3.internal.http2.Http2Stream incoming = server.newStream(Collections.singletonList(
                new okhttp3.internal.http2.Header("method", "GET")), true);
        connection.onStream(incoming);
        assertThat(incoming.getErrorCode()).isEqualTo(okhttp3.internal.http2.ErrorCode.REFUSED_STREAM);
        http2.close();
        server.close();
    }

    @Test
    void allocationLifecycleReportsRoutesAndCancellation() {
        okhttp3.internal.Internal.initializeInstanceForTests();
        Address address = address(80, null, Collections.singletonList(ConnectionSpec.CLEARTEXT));
        StreamAllocation allocation = new StreamAllocation(new ConnectionPool(), address, "call");
        assertThat(allocation.address).isSameAs(address);
        assertThat(allocation.codec()).isNull();
        assertThat(allocation.connection()).isNull();
        assertThat(allocation.hasMoreRoutes()).isTrue();
        assertThat(allocation.toString()).contains("example.com");
        allocation.noNewStreams();
        allocation.cancel();
        allocation.release();
        assertThat(allocation.hasMoreRoutes()).isTrue();

        ConnectionPool handoffPool = new ConnectionPool();
        Route handoffRoute = new Route(address, Proxy.NO_PROXY,
                new java.net.InetSocketAddress("localhost", 80));
        RealConnection oldConnection = RealConnection.testConnection(handoffPool, handoffRoute,
                new java.net.Socket(), 0L);
        RealConnection newConnection = RealConnection.testConnection(handoffPool, handoffRoute,
                new java.net.Socket(), 0L);
        StreamAllocation handoff = new StreamAllocation(handoffPool, address, "handoff");
        synchronized (handoffPool) {
            handoff.acquire(oldConnection);
            handoff.releaseAndAcquire(newConnection);
        }
        assertThat(handoff.connection()).isSameAs(newConnection);
    }

    private static Address address(int port, Proxy proxy, List<ConnectionSpec> specs) {
        ProxySelector selector = new ProxySelector() {
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(Proxy.NO_PROXY);
            }

            public void connectFailed(URI uri, SocketAddress address, java.io.IOException failure) {
            }
        };
        return new Address("example.com", port, Dns.SYSTEM, SocketFactory.getDefault(),
                null, null, CertificatePinner.DEFAULT, Authenticator.NONE, proxy,
                Collections.singletonList(Protocol.HTTP_1_1), specs, selector);
    }
}
