/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.AsyncNodeSelection;
import org.junit.jupiter.api.Test;

public class RedisAdvancedClusterAsyncCommandsImplTest {
    @Test
    void createsAsynchronousSelectionForClusterNodes() throws Exception {
        try (ClusterTestSupport.FakeRedisClusterServer server = new ClusterTestSupport.FakeRedisClusterServer()) {
            RedisClusterClient client = ClusterTestSupport.createClient(server);
            try (StatefulRedisClusterConnection<String, String> connection = client.connect()) {
                AsyncNodeSelection<String, String> selection = connection.async().nodes(node -> true);

                assertThat(selection.size()).isEqualTo(1);
                assertThat(selection.node(0).getNodeId()).isEqualTo(ClusterTestSupport.NODE_ID);
            } finally {
                ClusterTestSupport.shutdown(client);
            }
        }
    }
}
