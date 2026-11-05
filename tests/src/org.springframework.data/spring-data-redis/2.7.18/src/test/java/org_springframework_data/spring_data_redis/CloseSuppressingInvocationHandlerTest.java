/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;

public class CloseSuppressingInvocationHandlerTest {
    @Test
    void callbackCloseDoesNotCloseTheManagedConnection() throws Exception {
        try (RedisProtocolFixture redis = new RedisProtocolFixture()) {
            String response = redis.template().execute((RedisCallback<String>) connection -> {
                assertTrue(connection.equals(connection));
                assertFalse(connection.equals(new Object()));
                connection.close();
                assertFalse(connection.isClosed());
                return connection.ping();
            });

            assertEquals("PONG", response);
        }
    }
}
