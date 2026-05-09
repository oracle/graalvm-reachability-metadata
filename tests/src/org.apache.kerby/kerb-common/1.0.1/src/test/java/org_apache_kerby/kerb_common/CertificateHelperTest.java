/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import org.apache.kerby.kerberos.kerb.preauth.pkinit.CertificateHelper;
import org.junit.jupiter.api.Test;

public class CertificateHelperTest {
    private static final String RESOURCE_CERTIFICATE = "certificate-helper-test.pem";

    @Test
    void loadCertsReadsCertificateFromClasspathResource() throws Exception {
        List<Certificate> certificates = CertificateHelper.loadCerts(RESOURCE_CERTIFICATE);

        assertThat(certificates).hasSize(1);
        Certificate certificate = certificates.get(0);
        assertThat(certificate).isInstanceOf(X509Certificate.class);

        X509Certificate x509Certificate = (X509Certificate) certificate;
        assertThat(x509Certificate.getSubjectX500Principal().getName()).contains("CN=localhost");
    }
}
