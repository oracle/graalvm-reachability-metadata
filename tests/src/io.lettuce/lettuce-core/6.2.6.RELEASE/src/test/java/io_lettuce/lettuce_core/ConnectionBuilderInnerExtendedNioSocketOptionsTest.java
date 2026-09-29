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
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class ConnectionBuilderInnerExtendedNioSocketOptionsTest {
    @Test
    void appliesExtendedTcpKeepAliveOptionsToConnection() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = RedisClient.create(server.redisUri());
            SocketOptions.KeepAliveOptions keepAlive = SocketOptions.KeepAliveOptions.builder()
                    .enable()
                    .count(3)
                    .idle(Duration.ofSeconds(10))
                    .interval(Duration.ofSeconds(10))
                    .build();
            client.setOptions(ClientOptions.builder()
                    .protocolVersion(ProtocolVersion.RESP2)
                    .socketOptions(SocketOptions.builder()
                            .connectTimeout(LettuceTestSupport.TIMEOUT)
                            .keepAlive(keepAlive)
                            .build())
                    .build());
            try (StatefulRedisConnection<String, String> connection = LettuceTestSupport.connect(client)) {
                assertThat(connection.sync().ping()).isEqualTo("PONG");
            } finally {
                LettuceTestSupport.shutdown(client);
            }
        }
    }
}
