/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_auth;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hadoop.security.authentication.util.KerberosUtil;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class KerberosUtilTest {
    @Test
    void getsKerberosMechanismOidByName() throws Exception {
        Oid oid = KerberosUtil.getOidInstance("GSS_KRB5_MECH_OID");

        assertThat(oid.toString()).isEqualTo("1.2.840.113554.1.2.2");
    }

    @Test
    void readsDefaultRealmFromConfiguredKrb5File(@TempDir Path tempDir) throws Exception {
        Path krb5Conf = tempDir.resolve("krb5.conf");
        Files.writeString(krb5Conf, """
                [libdefaults]
                    default_realm = EXAMPLE.COM

                [realms]
                    EXAMPLE.COM = {
                        kdc = localhost
                    }
                """);

        String previousKrb5Conf = System.getProperty("java.security.krb5.conf");
        System.setProperty("java.security.krb5.conf", krb5Conf.toString());
        try {
            assertThat(KerberosUtil.getDefaultRealm()).isEqualTo("EXAMPLE.COM");
        } finally {
            if (previousKrb5Conf == null) {
                System.clearProperty("java.security.krb5.conf");
            } else {
                System.setProperty("java.security.krb5.conf", previousKrb5Conf);
            }
        }
    }
}
