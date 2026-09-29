/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.junit.jupiter.api.Test;

public class StatefulRedisPubSubConnectionImplTest {
    @Test
    void reconnectResubscribesToExistingChannels() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            client.setOptions(ClientOptions.builder()
                    .protocolVersion(ProtocolVersion.RESP2)
                    .autoReconnect(true)
                    .build());
            StatefulRedisPubSubConnection<String, String> connection = null;
            try {
                connection = client.connectPubSub();
                connection.setTimeout(LettuceTestSupport.TIMEOUT);
                connection.sync().subscribe("updates");
                server.disconnectClients();

                awaitCommandCount(server, "SUBSCRIBE", 2);

                assertThat(connection.isOpen()).isTrue();
                long subscriptions = server.commands().stream()
                        .filter(command -> command.get(0).equals("SUBSCRIBE"))
                        .count();
                assertThat(subscriptions).isGreaterThanOrEqualTo(2);
            } finally {
                close(connection);
                LettuceTestSupport.shutdown(client);
            }
        }
    }

    private static void awaitCommandCount(Lettuce_coreTest.FakeRedisServer server, String commandName, long expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + LettuceTestSupport.TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            long count = server.commands().stream().filter(command -> command.get(0).equals(commandName)).count();
            if (count >= expected) {
                return;
            }
            Thread.sleep(25);
        }
    }

    private static void close(StatefulRedisConnection<String, String> connection) {
        if (connection != null) {
            connection.close();
        }
    }
}
