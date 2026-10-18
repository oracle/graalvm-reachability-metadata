/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;

public class BouncyCastleUtilAnonymous1Test {

    @Test
    void generatesUsableCertificateAndPrivateKeyFiles() throws Exception {
        final SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        try {
            assertThat(certificate.certificate()).exists().isNotEmpty();
            assertThat(certificate.privateKey()).exists().isNotEmpty();
            assertThat(certificate.cert().getSubjectX500Principal().getName()).contains("localhost");
            assertThat(certificate.key().getAlgorithm()).isEqualTo("RSA");
        } finally {
            certificate.delete();
        }
    }
}
