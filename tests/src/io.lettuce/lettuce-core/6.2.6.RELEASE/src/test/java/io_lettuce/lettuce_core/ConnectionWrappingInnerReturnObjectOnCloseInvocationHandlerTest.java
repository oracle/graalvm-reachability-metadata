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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class ConnectionWrappingInnerReturnObjectOnCloseInvocationHandlerTest {
    @Test
    void syncApiDelegatesAndReturnsSameConnectionToPool() throws Exception {
        try (Lettuce_coreTest.FakeRedisServer server = new Lettuce_coreTest.FakeRedisServer()) {
            RedisClient client = LettuceTestSupport.createClient(server);
            BoundedAsyncPool<StatefulRedisConnection<String, String>> pool = AsyncConnectionPoolSupport
                    .createBoundedObjectPool(() -> client.connectAsync(StringCodec.UTF8, server.redisUri()),
                            BoundedPoolConfig.builder()
                                    .maxTotal(1)
                                    .maxIdle(1)
                                    .build());
            try {
                StatefulRedisConnection<String, String> first = acquire(pool);
                assertThat(first.sync().ping()).isEqualTo("PONG");
                first.close();

                StatefulRedisConnection<String, String> second = acquire(pool);
                assertThat(second).isNotSameAs(first);
                assertThat(pool.getObjectCount()).isEqualTo(1);
                assertThat(second.sync().ping()).isEqualTo("PONG");
                second.close();
            } finally {
                pool.closeAsync().get(LettuceTestSupport.TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                LettuceTestSupport.shutdown(client);
            }
        }
    }

    private static StatefulRedisConnection<String, String> acquire(
            BoundedAsyncPool<StatefulRedisConnection<String, String>> pool) throws Exception {
        return pool.acquire().get(LettuceTestSupport.TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }
}
