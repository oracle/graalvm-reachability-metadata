/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import java.time.Duration;

final class LettuceTestSupport {
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private LettuceTestSupport() {
    }

    static RedisClient createClient(Lettuce_coreTest.FakeRedisServer server) {
        RedisClient client = RedisClient.create(server.redisUri());
        client.setOptions(ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2)
                .build());
        return client;
    }

    static StatefulRedisConnection<String, String> connect(RedisClient client) {
        StatefulRedisConnection<String, String> connection = client.connect();
        connection.setTimeout(TIMEOUT);
        return connection;
    }

    static void shutdown(RedisClient client) {
        client.shutdown(Duration.ZERO, TIMEOUT);
    }
}
