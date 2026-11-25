/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLUtilBaseTest {
    @Test
    void instantiatesConfiguredTrustManager() throws Exception {
        SSLHostConfig host = new SSLHostConfig();
        host.setTrustManagerClassName(RejectingTrustManager.class.getName());
        SSLUtil utility = new JSSEImplementation().getSSLUtil(new SSLHostConfigCertificate(host, Type.RSA));
        TrustManager[] managers = utility.getTrustManagers();
        assertThat(managers).hasSize(1);
        assertThat(managers[0]).isInstanceOf(RejectingTrustManager.class);
        assertThat(((X509TrustManager) managers[0]).getAcceptedIssuers()).isEmpty();
    }

    public static class RejectingTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("No client certificates trusted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("No server certificates trusted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
