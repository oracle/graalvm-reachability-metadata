/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.io.netty.handler.ssl.util.SelfSignedCertificate;

public final class SelfSignedCertificateSupport {
    private SelfSignedCertificateSupport() {
    }

    public static void generateAndVerify() throws Exception {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        try {
            assertThat(certificate.cert().getSubjectX500Principal().getName()).contains("CN=localhost");
            assertThat(certificate.key().getAlgorithm()).isEqualTo("RSA");
            assertThat(certificate.certificate().isFile()).isTrue();
            assertThat(certificate.privateKey().isFile()).isTrue();
        } finally {
            certificate.delete();
        }
    }
}
