/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisTemplateTest {
    @Test
    void executesCommandsWithTheDefaultUnexposedConnection() throws Exception {
        try (RedisProtocolFixture redis = new RedisProtocolFixture()) {
            StringRedisTemplate template = redis.template();
            String result = template.execute((RedisCallback<String>) connection -> {
                assertTrue(connection.set(bytes("template-key"), bytes("stored-value")));
                return new String(connection.get(bytes("template-key")), StandardCharsets.UTF_8);
            });

            assertEquals("stored-value", result);
            assertEquals("stored-value", template.opsForValue().get("template-key"));
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
