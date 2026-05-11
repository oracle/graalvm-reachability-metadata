/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.kerby.kerberos.kerb.admin.Krb5Conf;
import org.apache.kerby.kerberos.kerb.server.KdcConfig;
import org.apache.kerby.kerberos.kerb.server.KdcConfigKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Krb5ConfTest {
    private final String previousKrb5Conf = System.getProperty(Krb5Conf.KRB5_CONF);

    @TempDir
    Path confDir;

    @AfterEach
    void restoreKrb5ConfProperty() {
        if (previousKrb5Conf == null) {
            System.clearProperty(Krb5Conf.KRB5_CONF);
        } else {
            System.setProperty(Krb5Conf.KRB5_CONF, previousKrb5Conf);
        }
    }

    @Test
    void writesTcpConfigurationFromClasspathTemplate() throws IOException {
        KdcConfig kdcConfig = newKdcConfig(false, 10088, 0);
        Krb5Conf krb5Conf = new Krb5Conf(confDir.toFile(), kdcConfig);

        krb5Conf.initKrb5conf();

        Path confFile = confDir.resolve("krb5.conf");
        assertThat(confFile).exists();
        assertThat(System.getProperty(Krb5Conf.KRB5_CONF)).isEqualTo(confFile.toFile().getAbsolutePath());

        String content = Files.readString(confFile);
        assertThat(content).contains("default_realm = EXAMPLE.COM");
        assertThat(content).contains("kdc = localhost:10088");
        assertThat(content).contains("kdc_tcp_port = 10088");
        assertThat(content).contains("udp_preference_limit = 1");
    }

    @Test
    void writesUdpConfigurationFromClasspathTemplate() throws IOException {
        KdcConfig kdcConfig = newKdcConfig(true, 10088, 10089);
        Krb5Conf krb5Conf = new Krb5Conf(confDir.toFile(), kdcConfig);

        krb5Conf.initKrb5conf();

        String content = Files.readString(confDir.resolve("krb5.conf"));
        assertThat(content).contains("default_realm = EXAMPLE.COM");
        assertThat(content).contains("kdc = localhost:10089");
        assertThat(content).contains("kdc_tcp_port = 10088");
        assertThat(content).contains("kdc_udp_port = 10089");
        assertThat(content).contains("udp_preference_limit = 4096");
    }

    private static KdcConfig newKdcConfig(boolean allowUdp, int tcpPort, int udpPort) {
        KdcConfig kdcConfig = new KdcConfig();
        kdcConfig.setString(KdcConfigKey.KDC_REALM, "EXAMPLE.COM");
        kdcConfig.setBoolean(KdcConfigKey.KDC_ALLOW_TCP, true);
        kdcConfig.setBoolean(KdcConfigKey.KDC_ALLOW_UDP, allowUdp);
        kdcConfig.setInt(KdcConfigKey.KDC_TCP_PORT, tcpPort);
        if (allowUdp) {
            kdcConfig.setInt(KdcConfigKey.KDC_UDP_PORT, udpPort);
        }
        return kdcConfig;
    }
}
