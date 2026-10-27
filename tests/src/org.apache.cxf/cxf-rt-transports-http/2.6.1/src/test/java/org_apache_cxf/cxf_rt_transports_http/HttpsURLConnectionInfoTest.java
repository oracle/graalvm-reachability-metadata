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
import java.security.cert.Certificate;

import org.apache.cxf.transport.https.HttpsURLConnectionInfo;
import org.junit.jupiter.api.Test;

public class HttpsURLConnectionInfoTest {
    @Test
    void readsSecurityDetailsFromAnAlternativeHttpsConnectionApi() throws Exception {
        Certificate[] localCertificates = new Certificate[0];
        Certificate[] serverCertificates = new Certificate[0];
        LegacyHttpsConnection connection =
                new LegacyHttpsConnection("TLS_TEST_SUITE", localCertificates, serverCertificates);
        connection.setRequestMethod("GET");

        HttpsURLConnectionInfo info = new HttpsURLConnectionInfo(connection);

        assertThat(info.getURL()).isEqualTo(connection.getURL());
        assertThat(info.getHttpRequestMethod()).isEqualTo("GET");
        assertThat(info.getEnabledCipherSuite()).isEqualTo("TLS_TEST_SUITE");
        assertThat(info.getLocalCertificates()).isSameAs(localCertificates);
        assertThat(info.getServerCertificates()).isSameAs(serverCertificates);
    }

    public static final class LegacyHttpsConnection extends HttpURLConnection {
        private final String cipherSuite;
        private final Certificate[] localCertificates;
        private final Certificate[] serverCertificates;

        LegacyHttpsConnection(
                String cipherSuite,
                Certificate[] localCertificates,
                Certificate[] serverCertificates)
                throws IOException {
            super(HttpsTestUrls.create("/resource"));
            this.cipherSuite = cipherSuite;
            this.localCertificates = localCertificates;
            this.serverCertificates = serverCertificates;
        }

        public String getCipherSuite() {
            return cipherSuite;
        }

        public Certificate[] getLocalCertificates() {
            return localCertificates;
        }

        public Certificate[] getServerCertificates() {
            return serverCertificates;
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
}
