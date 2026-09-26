/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.support.AsyncConnectionPoolSupport;
import io.lettuce.core.support.BoundedAsyncPool;
import io.lettuce.core.support.BoundedPoolConfig;
import io.lettuce.core.support.ConnectionWrapping;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ConnectionWrappingTest {
    @Test
    void wrappedConnectionCanBeUnwrappedAndReturnedOnClose() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = LettuceTestSupport.createClient(server);
            BoundedAsyncPool<StatefulRedisConnection<String, String>> pool = AsyncConnectionPoolSupport
                    .createBoundedObjectPool(() -> client.connectAsync(StringCodec.UTF8, server.redisUri()),
                            BoundedPoolConfig.builder()
                                    .maxTotal(1)
                                    .maxIdle(1)
                                    .build());
            try {
                StatefulRedisConnection<String, String> connection = pool.acquire()
                        .get(LettuceTestSupport.TIMEOUT.toSeconds(), TimeUnit.SECONDS);

                assertThat(ConnectionWrapping.unwrap(connection)).isNotSameAs(connection);
                connection.close();
                assertThat(pool.getIdle()).isEqualTo(1);
            } finally {
                pool.closeAsync().get(LettuceTestSupport.TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                LettuceTestSupport.shutdown(client);
            }
        }
    }
}
