/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;

import sun.security.krb5.KrbException;

import static org.assertj.core.api.Assertions.assertThat;

public class OrgApacheKafkaCommonSecurityKerberosKerberosErrorTest {

    @Test
    void testFromExceptionExtractsKerberosReturnCode() {
        String originalVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");
        try {
            SaslException exception = new SaslException("SASL failure", new KrbException(21));

            KerberosError error = KerberosError.fromException(exception);

            assertThat(error).isEqualTo(KerberosError.CLIENT_NOT_YET_VALID);
            assertThat(error.retriable()).isTrue();
        } finally {
            restoreJavaVendor(originalVendor);
        }
    }

    private static void restoreJavaVendor(String originalVendor) {
        if (originalVendor == null) {
            System.clearProperty("java.vendor");
        } else {
            System.setProperty("java.vendor", originalVendor);
        }
    }
}
