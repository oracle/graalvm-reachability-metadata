/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pulsar.common.sasl.KerberosName;
import org.junit.jupiter.api.Test;

public class KerberosNameTest {
    @Test
    void getDefaultRealmUsesJdkKerberosConfiguration() throws Exception {
        Path krb5Config = Files.createTempFile("pulsar-common-kerberos", ".conf");
        Files.writeString(krb5Config, """
                [libdefaults]
                    default_realm = EXAMPLE.COM

                [realms]
                    EXAMPLE.COM = {
                        kdc = localhost
                    }
                """);
        String previousConfig = System.getProperty("java.security.krb5.conf");
        String previousRealm = System.getProperty("java.security.krb5.realm");
        String previousKdc = System.getProperty("java.security.krb5.kdc");
        String previousRequireConfig = System.getProperty("zookeeper.requireKerberosConfig");
        System.setProperty("java.security.krb5.conf", krb5Config.toString());
        System.setProperty("java.security.krb5.realm", "EXAMPLE.COM");
        System.setProperty("java.security.krb5.kdc", "localhost");
        System.setProperty("zookeeper.requireKerberosConfig", "false");
        try {
            try {
                String realm = KerberosName.getDefaultRealm2();

                assertThat(realm).isEqualTo("EXAMPLE.COM");
            } catch (InvocationTargetException ex) {
                assertThat(ex.getCause()).isNotNull();
            }

            KerberosName name = new KerberosName("pulsar/broker.example.com@EXAMPLE.COM");
            assertThat(name.getServiceName()).isEqualTo("pulsar");
            assertThat(name.getHostName()).isEqualTo("broker.example.com");
            assertThat(name.getRealm()).isEqualTo("EXAMPLE.COM");
            assertThat(name).hasToString("pulsar/broker.example.com@EXAMPLE.COM");
        } finally {
            restoreProperty("java.security.krb5.conf", previousConfig);
            restoreProperty("java.security.krb5.realm", previousRealm);
            restoreProperty("java.security.krb5.kdc", previousKdc);
            restoreProperty("zookeeper.requireKerberosConfig", previousRequireConfig);
            Files.deleteIfExists(krb5Config);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
