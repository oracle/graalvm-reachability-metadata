/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import org.junit.jupiter.api.Test;
import org.postgresql.ssl.SingleCertValidatingFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleCertValidatingFactoryTest {

    private static final String CERTIFICATE_RESOURCE = "postgresql/single-cert-validating-factory-cert.pem";

    @Test
    void loadsCertificateFromThreadContextClassLoaderResource() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(SingleCertValidatingFactoryTest.class.getClassLoader());
        try {
            SingleCertValidatingFactory factory = new SingleCertValidatingFactory(
                    "classpath:" + CERTIFICATE_RESOURCE);

            assertThat(factory.getSupportedCipherSuites()).isNotEmpty();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void loadsCertificateFromFactoryClassResourceWhenThreadContextClassLoaderIsUnavailable()
            throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            SingleCertValidatingFactory factory = new SingleCertValidatingFactory(
                    "classpath:/" + CERTIFICATE_RESOURCE);

            assertThat(factory.getDefaultCipherSuites()).isNotEmpty();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }
}
