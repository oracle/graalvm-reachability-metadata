/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.env.SecurityConfig;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityConfigTest {
    @Test
    void exercisesPublicApi() throws Exception {
        List<X509Certificate> certificates = SecurityConfig.capellaCaCertificates();
        assertFalse(certificates.isEmpty());
        for (X509Certificate certificate : certificates) {
            assertTrue(certificate.getBasicConstraints() >= 0);
            assertFalse(certificate.getSubjectX500Principal().getName().isEmpty());
            assertTrue(certificate.getEncoded().length > 0);
        }
        SecurityConfig config = SecurityConfig.builder().enableTls(true).trustCertificates(certificates).build();
        assertTrue(config.tlsEnabled());
        assertEquals(certificates, config.trustCertificates());
    }
}
