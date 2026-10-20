/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_transports_http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.transport.https.HttpsURLConnectionFactory;
import org.junit.jupiter.api.Test;

public class HttpsURLConnectionFactoryTest {
    @Test
    void decoratesAnAlternativeHttpsConnectionApi() throws Exception {
        LegacyHttpsConnection connection = new LegacyHttpsConnection();
        URL url = new URL("https", "localhost", 443, "/", new ConnectionHandler(connection));
        TLSClientParameters parameters = new TLSClientParameters();
        parameters.setDisableCNCheck(true);
        parameters.setUseHttpsURLConnectionDefaultSslSocketFactory(true);

        HttpURLConnection result = new HttpsURLConnectionFactory().createConnection(parameters, null, url);

        assertThat(result).isSameAs(connection);
        assertThat(connection.getHostnameVerifier()).isNotNull();
        assertThat(connection.getHostnameVerifier().verify("localhost", null)).isTrue();
        assertThat(connection.getSSLSocketFactory()).isNotNull();
        assertThat(connection.getSSLSocketFactory().getDelegate()).isNotNull();
    }

    @Test
    void installsAStandardSocketFactoryOnAnAlternativeHttpsConnection() throws Exception {
        DirectHttpsConnection connection = new DirectHttpsConnection();
        URL url = new URL("https", "localhost", 443, "/", new ConnectionHandler(connection));
        TLSClientParameters parameters = new TLSClientParameters();
        parameters.setUseHttpsURLConnectionDefaultSslSocketFactory(true);

        HttpURLConnection result = new HttpsURLConnectionFactory().createConnection(parameters, null, url);

        assertThat(result).isSameAs(connection);
        assertThat(connection.getSSLSocketFactory())
                .isSameAs(HttpsURLConnection.getDefaultSSLSocketFactory());
    }

    public interface LegacyHostnameVerifier {
        boolean verify(String host, SSLSession session);
    }

    public static final class LegacySocketFactory {
        private final SSLSocketFactory delegate;

        protected LegacySocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        public SSLSocketFactory getDelegate() {
            return delegate;
        }
    }

    public static final class DirectHttpsConnection extends HttpURLConnection {
        private SSLSocketFactory socketFactory;

        DirectHttpsConnection() throws IOException {
            super(new URL("https://localhost/"));
        }

        public SSLSocketFactory getSSLSocketFactory() {
            return socketFactory;
        }

        public void setSSLSocketFactory(SSLSocketFactory socketFactory) {
            this.socketFactory = socketFactory;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }
    }

    public static final class LegacyHttpsConnection extends HttpURLConnection {
        private LegacyHostnameVerifier hostnameVerifier;
        private LegacySocketFactory socketFactory;

        LegacyHttpsConnection() throws IOException {
            super(new URL("https://localhost/"));
        }

        public LegacyHostnameVerifier getHostnameVerifier() {
            return hostnameVerifier;
        }

        public void setHostnameVerifier(LegacyHostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
        }

        public LegacySocketFactory getSSLSocketFactory() {
            return socketFactory;
        }

        public void setSSLSocketFactory(LegacySocketFactory socketFactory) {
            this.socketFactory = socketFactory;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }
    }

    private static final class ConnectionHandler extends URLStreamHandler {
        private final HttpURLConnection connection;

        private ConnectionHandler(HttpURLConnection connection) {
            this.connection = connection;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return connection;
        }
    }
}
