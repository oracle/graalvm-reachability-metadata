/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ibm.security.krb5.KrbException;

import org.apache.kafka.common.security.kerberos.KerberosError;
import org.junit.jupiter.api.Test;

public class KerberosErrorTest {
    @Test
    void resolvesKerberosErrorFromIbmKrbExceptionCause() {
        String originalVendor = System.getProperty("java.vendor");
        System.setProperty("java.vendor", "IBM Corporation");
        try {
            Exception exception = new Exception(new KrbException(7));

            KerberosError error = KerberosError.fromException(exception);

            assertSame(KerberosError.SERVER_NOT_FOUND, error);
            assertFalse(error.retriable());
        } finally {
            restoreProperty("java.vendor", originalVendor);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
