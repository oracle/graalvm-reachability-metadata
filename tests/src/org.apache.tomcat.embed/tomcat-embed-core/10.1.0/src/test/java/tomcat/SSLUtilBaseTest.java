/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.apache.tomcat.util.net.SSLUtil;
import org.apache.tomcat.util.net.jsse.JSSEImplementation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SSLUtilBaseTest {
    @Test
    void constructsConfiguredTrustManagerAndUsesItsCertificatePolicy() throws Exception {
        SSLHostConfig config = new SSLHostConfig();
        config.setTrustManagerClassName(ApplicationTrustManager.class.getName());
        SSLUtil util = new JSSEImplementation().getSSLUtil(new SSLHostConfigCertificate(config, Type.RSA));
        TrustManager[] managers = util.getTrustManagers();
        assertEquals(1, managers.length);
        ApplicationTrustManager manager = assertInstanceOf(ApplicationTrustManager.class, managers[0]);
        assertEquals(0, manager.getAcceptedIssuers().length);
        assertThrows(CertificateException.class,
                () -> manager.checkServerTrusted(new X509Certificate[0], "RSA"));
    }

    public static class ApplicationTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;

        public ApplicationTrustManager() throws Exception {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            delegate = (X509TrustManager) factory.getTrustManagers()[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            validateChain(chain);
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            validateChain(chain);
            delegate.checkServerTrusted(chain, authType);
        }

        private void validateChain(X509Certificate[] chain) throws CertificateException {
            if (chain.length == 0) {
                throw new CertificateException("An empty certificate chain is not trusted");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
