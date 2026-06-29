/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pulsar.common.sasl.KerberosName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class KerberosNameTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesDefaultRealmFromConfiguredKerberosFile() throws Exception {
        final Path configuration = tempDir.resolve("krb5.conf");
        Files.writeString(configuration, """
                [libdefaults]
                    default_realm = EXAMPLE.COM

                [realms]
                    EXAMPLE.COM = {
                        kdc = localhost
                    }
                """);

        final String previousKerberosConfiguration = System.getProperty("java.security.krb5.conf");
        final String previousKerberosRealm = System.getProperty("java.security.krb5.realm");
        final String previousKerberosKdc = System.getProperty("java.security.krb5.kdc");
        final String previousRequiredConfiguration = System.getProperty("zookeeper.requireKerberosConfig");
        System.setProperty("java.security.krb5.conf", configuration.toAbsolutePath().toString());
        System.setProperty("java.security.krb5.realm", "EXAMPLE.COM");
        System.setProperty("java.security.krb5.kdc", "localhost");
        System.clearProperty("zookeeper.requireKerberosConfig");
        try {
            final String defaultRealm = KerberosName.getDefaultRealm2();
            final KerberosName name = new KerberosName("broker/localhost@EXAMPLE.COM");

            assertThat(defaultRealm).isEqualTo("EXAMPLE.COM");
            assertThat(name.getServiceName()).isEqualTo("broker");
            assertThat(name.getHostName()).isEqualTo("localhost");
            assertThat(name.getRealm()).isEqualTo("EXAMPLE.COM");
            assertThat(name.toString()).isEqualTo("broker/localhost@EXAMPLE.COM");
        } finally {
            restoreProperty("java.security.krb5.conf", previousKerberosConfiguration);
            restoreProperty("java.security.krb5.realm", previousKerberosRealm);
            restoreProperty("java.security.krb5.kdc", previousKerberosKdc);
            restoreProperty("zookeeper.requireKerberosConfig", previousRequiredConfiguration);
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
