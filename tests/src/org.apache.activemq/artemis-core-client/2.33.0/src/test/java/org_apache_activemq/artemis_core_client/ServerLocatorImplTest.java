/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.junit.jupiter.api.Test;

public class ServerLocatorImplTest {
    @Test
    void updatesTopologyArrayWhenClusterNodeIsAnnounced() {
        TransportConfiguration initial = nettyConnector("initial-live");
        TransportConfiguration live = nettyConnector("node-live");
        TransportConfiguration backup = nettyConnector("node-backup");

        try (ServerLocator locator = ActiveMQClient.createServerLocatorWithHA(initial)) {
            ServerLocatorInternal internalLocator = (ServerLocatorInternal) locator;

            internalLocator.notifyNodeUp(
                    1L, "node-a", "backup-group", "scale-down-group", new Pair<>(live, backup), true);

            assertThat(internalLocator.getTopology().getMembers()).hasSize(1);
            assertThat(internalLocator.getTopology().getMember("node-a").getPrimary()).isEqualTo(live);
            assertThat(internalLocator.getTopology().getMember("node-a").getBackup()).isEqualTo(backup);
        }
    }

    private static TransportConfiguration nettyConnector(String name) {
        return new TransportConfiguration(NettyConnectorFactory.class.getName(), Map.of("host", "127.0.0.1"), name);
    }
}
