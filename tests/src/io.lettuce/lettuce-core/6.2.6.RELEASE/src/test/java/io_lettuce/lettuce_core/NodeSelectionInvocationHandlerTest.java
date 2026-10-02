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
import io.lettuce.core.cluster.api.sync.Executions;
import org.junit.jupiter.api.Test;

public class NodeSelectionInvocationHandlerTest {
    @Test
    void executesCommandAcrossSelectedClusterNodes() throws Exception {
        try (ClusterTestSupport.FakeRedisClusterServer server = new ClusterTestSupport.FakeRedisClusterServer()) {
            RedisClusterClient client = ClusterTestSupport.createClient(server);
            try (StatefulRedisClusterConnection<String, String> connection = client.connect()) {
                connection.setTimeout(LettuceTestSupport.TIMEOUT);
                Executions<String> pings = connection.sync().nodes(node -> true).commands().ping();

                assertThat(pings).containsExactly("PONG");
                assertThat(pings.nodes()).hasSize(1);
            } finally {
                ClusterTestSupport.shutdown(client);
            }
        }
    }
}
