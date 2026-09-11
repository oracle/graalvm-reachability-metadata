/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package okhttp3.internal.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

public class AndroidPlatformDeepCoverageTest {
    @Test
    void androidPlatformPublicSocketAndTlsFallbacksWork() throws Exception {
        AndroidPlatform platform = new AndroidPlatform(FakeParameters.class, null, null, null,
                null);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try {
                    server.accept().close();
                } catch (Exception ignored) {
                }
            });
            acceptor.start();
            Socket socket = new Socket();
            platform.connectSocket(socket,
                    new InetSocketAddress("localhost", server.getLocalPort()), 1000);
            assertThat(socket.isConnected()).isTrue();
            socket.close();
            acceptor.join(1000L);
        }

        assertThat(platform.isCleartextTrafficPermitted("example.com")).isTrue();
        platform.log(Platform.INFO, "android coverage", new IllegalStateException("logged"));
        X509TrustManager expected = new EmptyTrustManager();
        X509TrustManager trustManager = platform.trustManager(new FakeSocketFactory(expected));
        assertThat(trustManager).isSameAs(expected);
    }

    private static final class FakeParameters {
        private final X509TrustManager x509TrustManager;

        private FakeParameters(X509TrustManager x509TrustManager) {
            this.x509TrustManager = x509TrustManager;
        }
    }

    private static final class FakeSocketFactory extends SSLSocketFactory {
        private final FakeParameters sslParameters;

        private FakeSocketFactory(X509TrustManager trustManager) {
            sslParameters = new FakeParameters(trustManager);
        }

        @Override public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        @Override public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override public Socket createSocket(Socket socket, String host, int port,
                boolean autoClose) {
            return socket;
        }

        @Override public Socket createSocket(String host, int port) {
            return new Socket();
        }

        @Override public Socket createSocket(String host, int port,
                java.net.InetAddress localHost, int localPort) {
            return new Socket();
        }

        @Override public Socket createSocket(java.net.InetAddress host, int port) {
            return new Socket();
        }

        @Override public Socket createSocket(java.net.InetAddress address, int port,
                java.net.InetAddress localAddress, int localPort) {
            return new Socket();
        }
    }

    private static final class EmptyTrustManager implements X509TrustManager {
        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                String authType) {
        }

        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                String authType) {
        }

        @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[0];
        }
    }
}
