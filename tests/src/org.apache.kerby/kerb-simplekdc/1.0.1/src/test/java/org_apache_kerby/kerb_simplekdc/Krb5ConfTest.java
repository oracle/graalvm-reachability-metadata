/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_simplekdc;

import org.apache.kerby.kerberos.kerb.client.Krb5Conf;
import org.apache.kerby.kerberos.kerb.client.KrbConfig;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class Krb5ConfTest {
    @Test
    void initKrb5confLoadsTcpTemplateResourceAndWritesConf(@TempDir Path workDir) throws Exception {
        SimpleKdcServer server = newConfiguredServer(workDir, 31000, false);

        GeneratedConf generatedConf = generateConf(server);

        assertThat(generatedConf.confFile()).exists().isRegularFile();
        assertThat(generatedConf.content())
                .contains("kdc_realm = NATIVE-TEST.EXAMPLE")
                .contains("default_realm = NATIVE-TEST.EXAMPLE")
                .contains("kdc = localhost:31000")
                .contains("kdc_tcp_port = 31000")
                .contains("udp_preference_limit = 1")
                .doesNotContain("_REALM_", "_KDC_PORT_", "#_KDC_TCP_PORT_");
    }

    @Test
    void initKrb5confLoadsUdpTemplateResourceAndWritesConf(@TempDir Path workDir) throws Exception {
        SimpleKdcServer server = newConfiguredServer(workDir, 32000, true);

        GeneratedConf generatedConf = generateConf(server);

        assertThat(generatedConf.confFile()).exists().isRegularFile();
        assertThat(generatedConf.content())
                .contains("kdc_realm = NATIVE-TEST.EXAMPLE")
                .contains("default_realm = NATIVE-TEST.EXAMPLE")
                .contains("kdc = localhost:32000")
                .contains("kdc_udp_port = 32000")
                .contains("udp_preference_limit = 4096")
                .doesNotContain("_REALM_", "_KDC_PORT_", "#_KDC_UDP_PORT_");
    }

    private static SimpleKdcServer newConfiguredServer(
            Path workDir, int port, boolean udp) throws Exception {
        SimpleKdcServer server = new SimpleKdcServer(new KrbConfig());
        server.setWorkDir(workDir.toFile());
        server.setKdcRealm("NATIVE-TEST.EXAMPLE");
        server.setKdcHost("localhost");
        if (udp) {
            server.setKdcUdpPort(port);
            server.setAllowTcp(false);
        } else {
            server.setKdcTcpPort(port);
            server.setAllowUdp(false);
        }
        return server;
    }

    private static GeneratedConf generateConf(SimpleKdcServer server) throws Exception {
        String previousConf = System.getProperty(Krb5Conf.KRB5_CONF);
        try {
            Krb5Conf krb5Conf = new Krb5Conf(server);
            krb5Conf.initKrb5conf();
            Path confFile = Path.of(System.getProperty(Krb5Conf.KRB5_CONF));
            String content = Files.readString(confFile, StandardCharsets.UTF_8);
            return new GeneratedConf(confFile, content);
        } finally {
            if (previousConf == null) {
                System.clearProperty(Krb5Conf.KRB5_CONF);
            } else {
                System.setProperty(Krb5Conf.KRB5_CONF, previousConf);
            }
        }
    }

    private static final class GeneratedConf {
        private final Path confFile;
        private final String content;

        private GeneratedConf(Path confFile, String content) {
            this.confFile = confFile;
            this.content = content;
        }

        private Path confFile() {
            return confFile;
        }

        private String content() {
            return content;
        }
    }
}
