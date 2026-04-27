/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.security.krb5.KrbException;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class KerberosErrorTest {

    @Test
    void valuesAndFromExceptionCoverKerberosLookupPaths() throws Exception {
        String originalJavaVendor = System.getProperty("java.vendor");

        try {
            System.setProperty("java.vendor", "IBM Semeru Runtime");

            assertThat(KerberosError.values()).contains(KerberosError.REPLAY);

            Exception wrapper = new Exception("outer", new RuntimeException(new KrbException(34)));

            assertThat(KerberosError.fromException(wrapper)).isEqualTo(KerberosError.REPLAY);
        } finally {
            restoreJavaVendor(originalJavaVendor);
        }
    }

    private static void restoreJavaVendor(String originalJavaVendor) {
        if (originalJavaVendor == null) {
            System.clearProperty("java.vendor");
        } else {
            System.setProperty("java.vendor", originalJavaVendor);
        }
    }
}
