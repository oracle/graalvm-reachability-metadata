/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.internal.platform.Platform;
import org.junit.jupiter.api.Test;

public class PlatformTest {
    @Test
    void trustManagerReadsPrivateContextFieldFromSocketFactory() {
        X509TrustManager trustManager = new Platform()
                .trustManager(new NullContextSslSocketFactory());

        assertThat(trustManager).isNull();
    }

    private static final class NullContextSslSocketFactory extends SSLSocketFactory {
        @SuppressWarnings("unused")
        private final Object context = null;

        @Override
        public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
