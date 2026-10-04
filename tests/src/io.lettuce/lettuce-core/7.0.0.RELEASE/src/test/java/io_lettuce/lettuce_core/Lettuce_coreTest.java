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
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class Lettuce_coreTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void redisUriAndClientOptionsCanBeConfiguredWithoutConnecting() {
        RedisURI uri = RedisURI.Builder.redis("localhost", 6380)
                .withDatabase(2)
                .withSsl(false)
                .withTimeout(COMMAND_TIMEOUT)
                .build();

        RedisClient client = RedisClient.create(uri);
        try {
            SocketOptions socketOptions = SocketOptions.builder()
                    .connectTimeout(COMMAND_TIMEOUT)
                    .keepAlive(true)
                    .build();
            ClientOptions clientOptions = ClientOptions.builder()
                    .autoReconnect(false)
                    .pingBeforeActivateConnection(true)
                    .socketOptions(socketOptions)
                    .build();

            client.setOptions(clientOptions);

            assertThat(uri.getHost()).isEqualTo("localhost");
            assertThat(uri.getPort()).isEqualTo(6380);
            assertThat(uri.getDatabase()).isEqualTo(2);
            assertThat(uri.getTimeout()).isEqualTo(COMMAND_TIMEOUT);
            assertThat(client.getOptions()).isSameAs(clientOptions);
        } finally {
            client.shutdown(Duration.ZERO, COMMAND_TIMEOUT);
        }
    }
}
