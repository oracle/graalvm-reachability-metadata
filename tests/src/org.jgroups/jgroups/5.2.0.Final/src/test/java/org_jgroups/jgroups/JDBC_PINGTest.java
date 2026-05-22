/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.JChannel;
import org.jgroups.protocols.JDBC_PING;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class JDBC_PINGTest {
    private static final String H2_DRIVER = "org.h2.Driver";

    @Test
    void loadsConfiguredDriverDuringProtocolInitialization() throws Exception {
        String databaseUrl = "jdbc:h2:mem:jgroups_ping_init;DB_CLOSE_DELAY=-1";
        JDBC_PING discovery = configuredJdbcPing(databaseUrl);

        try (JChannel channel = new JChannel(new SHARED_LOOPBACK(), discovery)) {
            JDBC_PING configuredDiscovery = channel.getProtocolStack().findProtocol(JDBC_PING.class);

            assertThat(configuredDiscovery).isSameAs(discovery);
        }
    }

    @Test
    void commandLineReaderLoadsDriverAndQueriesPingTable() throws Exception {
        String databaseUrl = "jdbc:h2:mem:jgroups_ping_main;DB_CLOSE_DELAY=-1";
        String clusterName = "test-cluster";
        String select = "SELECT CAST(NULL AS VARBINARY) AS ping_data, '' AS own_addr, ? AS cluster_name WHERE 1=0";

        assertThatCode(() -> JDBC_PING.main(new String[] {
                "-driver", H2_DRIVER,
                "-conn", databaseUrl,
                "-user", "sa",
                "-pwd", "",
                "-cluster", clusterName,
                "-select", select
        })).doesNotThrowAnyException();
    }

    private static JDBC_PING configuredJdbcPing(String databaseUrl) {
        JDBC_PING discovery = new JDBC_PING();
        discovery.setValue("connection_url", databaseUrl);
        discovery.setValue("connection_driver", H2_DRIVER);
        discovery.setValue("connection_username", "sa");
        discovery.setValue("connection_password", "");
        discovery.registerShutdownHook(false);
        return discovery;
    }
}
