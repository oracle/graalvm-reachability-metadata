/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_registry_statsd;

import io.micrometer.shaded.io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenJdkSelfSignedCertGeneratorTest {
    @Test
    void generatesUsableSelfSignedCertificate() throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 3_600_000L);
        SelfSignedCertificate generated = new SelfSignedCertificate("localhost", notBefore, notAfter, "RSA", 2048);

        try {
            X509Certificate certificate = generated.cert();

            assertThat(certificate.getSubjectX500Principal()).isEqualTo(new X500Principal("CN=localhost"));
            assertThat(certificate.getIssuerX500Principal()).isEqualTo(certificate.getSubjectX500Principal());
            assertThat(certificate.getSerialNumber()).isPositive();
            assertThat(generated.key().getAlgorithm()).isEqualTo("RSA");
            assertThat(generated.certificate()).isFile();
            assertThat(generated.privateKey()).isFile();
            certificate.checkValidity(new Date());
            certificate.verify(certificate.getPublicKey());
        }
        finally {
            generated.delete();
        }
    }
}
