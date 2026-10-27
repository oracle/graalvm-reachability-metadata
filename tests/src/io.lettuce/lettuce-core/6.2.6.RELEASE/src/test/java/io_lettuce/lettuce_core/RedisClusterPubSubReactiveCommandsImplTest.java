/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.cluster.pubsub.api.reactive.PubSubReactiveNodeSelection;
import org.junit.jupiter.api.Test;

public class RedisClusterPubSubReactiveCommandsImplTest {
    @Test
    void createsReactiveSelectionForPubSubNodes() throws Exception {
        try (ClusterTestSupport.FakeRedisClusterServer server = new ClusterTestSupport.FakeRedisClusterServer()) {
            RedisClusterClient client = ClusterTestSupport.createClient(server);
            try (StatefulRedisClusterPubSubConnection<String, String> connection = client.connectPubSub()) {
                PubSubReactiveNodeSelection<String, String> selection = connection.reactive().nodes(node -> true);

                assertThat(selection.size()).isEqualTo(1);
                assertThat(selection.node(0).getNodeId()).isEqualTo(ClusterTestSupport.NODE_ID);
            } finally {
                ClusterTestSupport.shutdown(client);
            }
        }
    }
}
