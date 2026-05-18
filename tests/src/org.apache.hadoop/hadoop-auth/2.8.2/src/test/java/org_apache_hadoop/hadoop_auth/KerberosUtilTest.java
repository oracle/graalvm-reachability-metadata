/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_auth;

import java.io.IOException;
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
        Path krb5Conf = writeKrb5Conf(tempDir);

        String previousKrb5Conf = System.getProperty("java.security.krb5.conf");
        System.setProperty("java.security.krb5.conf", krb5Conf.toString());
        try {
            assertThat(KerberosUtil.getDefaultRealm()).isEqualTo("DEFAULT.EXAMPLE.COM");
        } finally {
            restoreKrb5Conf(previousKrb5Conf);
        }
    }

    @Test
    void mapsServiceHostDomainToConfiguredRealm(@TempDir Path tempDir) throws Exception {
        Path krb5Conf = writeKrb5Conf(tempDir);

        String previousKrb5Conf = System.getProperty("java.security.krb5.conf");
        System.setProperty("java.security.krb5.conf", krb5Conf.toString());
        try {
            assertThat(KerberosUtil.getDomainRealm("HTTP/service.example.com"))
                    .isEqualTo("DOMAIN.EXAMPLE.COM");
        } finally {
            restoreKrb5Conf(previousKrb5Conf);
        }
    }

    private static Path writeKrb5Conf(Path tempDir) throws IOException {
        Path krb5Conf = tempDir.resolve("krb5.conf");
        Files.writeString(krb5Conf, """
                [libdefaults]
                    default_realm = DEFAULT.EXAMPLE.COM

                [realms]
                    DEFAULT.EXAMPLE.COM = {
                        kdc = localhost
                    }
                    DOMAIN.EXAMPLE.COM = {
                        kdc = localhost
                    }

                [domain_realm]
                    example.com = DOMAIN.EXAMPLE.COM
                    .example.com = DOMAIN.EXAMPLE.COM
                """);
        return krb5Conf;
    }

    private static void restoreKrb5Conf(String previousKrb5Conf) {
        if (previousKrb5Conf == null) {
            System.clearProperty("java.security.krb5.conf");
        } else {
            System.setProperty("java.security.krb5.conf", previousKrb5Conf);
        }
    }
}
