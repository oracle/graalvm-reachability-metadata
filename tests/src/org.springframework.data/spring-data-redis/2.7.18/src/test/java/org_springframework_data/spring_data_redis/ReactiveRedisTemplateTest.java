/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

public class ReactiveRedisTemplateTest {
    @Test
    void reactiveCallbackCanPingAfterClosingItsManagedConnection() throws Exception {
        try (RedisProtocolFixture redis = new RedisProtocolFixture()) {
            ReactiveRedisTemplate<String, String> template = new ReactiveRedisTemplate<>(
                    redis.factory, RedisSerializationContext.string());

            List<String> responses = template.execute(connection -> {
                connection.close();
                return connection.ping();
            }).collectList().block(Duration.ofSeconds(10));

            assertEquals(List.of("PONG"), responses);
            assertEquals(Boolean.TRUE, template.opsForValue().set("reactive-key", "reactive-value")
                    .block(Duration.ofSeconds(10)));
            assertEquals("reactive-value", template.opsForValue().get("reactive-key")
                    .block(Duration.ofSeconds(10)));
        }
    }
}
