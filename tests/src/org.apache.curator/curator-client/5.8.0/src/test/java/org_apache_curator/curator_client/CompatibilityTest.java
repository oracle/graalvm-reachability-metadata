/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.net.InetSocketAddress;

import org.apache.curator.utils.Compatibility;
import org.apache.zookeeper.server.quorum.QuorumPeer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompatibilityTest {
    @Test
    void resolvesQuorumServerHostThroughTheSupportedCompatibilityApi() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 2888);
        QuorumPeer.QuorumServer server = new QuorumPeer.QuorumServer(1, address);

        assertThat(Compatibility.hasGetReachableOrOneMethod()).isTrue();
        assertThat(Compatibility.hasAddrField()).isTrue();
        assertThat(Compatibility.getHostString(server)).isEqualTo("127.0.0.1");
        assertThat(Compatibility.hasPersistentWatchers()).isTrue();
    }
}
