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
import io.lettuce.core.cluster.api.sync.NodeSelection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import org.junit.jupiter.api.Test;

public class ClusterFutureSyncInvocationHandlerTest {
    @Test
    void synchronousClusterApiExposesNodeConnectionAndSelection() throws Exception {
        try (ClusterTestSupport.FakeRedisClusterServer server = new ClusterTestSupport.FakeRedisClusterServer()) {
            RedisClusterClient client = ClusterTestSupport.createClient(server);
            try (StatefulRedisClusterConnection<String, String> connection = client.connect()) {
                connection.setTimeout(LettuceTestSupport.TIMEOUT);
                RedisAdvancedClusterCommands<String, String> commands = connection.sync();
                NodeSelection<String, String> selection = commands.nodes(node -> true);

                assertThat(commands.getConnection(ClusterTestSupport.NODE_ID).ping()).isEqualTo("PONG");
                assertThat(selection.size()).isEqualTo(1);
            } finally {
                ClusterTestSupport.shutdown(client);
            }
        }
    }
}
