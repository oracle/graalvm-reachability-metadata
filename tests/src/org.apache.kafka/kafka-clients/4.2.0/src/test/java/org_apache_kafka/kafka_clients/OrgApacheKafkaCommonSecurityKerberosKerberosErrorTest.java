/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.ietf.jgss.GSSException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonSecurityKerberosKerberosErrorTest {
    private static String originalJavaVendor;

    @BeforeAll
    static void useIbmVendorForKerberosImplementationSelection() {
        originalJavaVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");
    }

    @AfterAll
    static void restoreJavaVendor() {
        if (originalJavaVendor == null) {
            System.clearProperty("java.vendor");
        } else {
            System.setProperty("java.vendor", originalJavaVendor);
        }
    }

    @Test
    void testKerberosErrorRecognizesRetriableGssExceptionCause() throws Exception {
        SaslException exception = new SaslException(
                "credentials are unavailable",
                new GSSException(GSSException.NO_CRED));

        assertThat(KerberosError.isRetriableClientGssException(exception)).isTrue();
    }
}
