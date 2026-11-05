/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RedisConnectionUtilsInnerConnectionSplittingInterceptorTest {
    @Test
    void readsCommittedValueWhileWritesStayQueuedUntilCommit() throws Exception {
        try (RedisProtocolFixture redis = new RedisProtocolFixture()) {
            redis.template().opsForValue().set("transaction-key", "before");
            RedisTemplate<byte[], byte[]> template = new RedisTemplate<>();
            template.setConnectionFactory(redis.factory);
            template.afterPropertiesSet();
            template.setEnableTransactionSupport(true);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            try {
                String visible = template.execute((RedisCallback<String>) connection -> {
                    byte[] key = "transaction-key".getBytes(StandardCharsets.UTF_8);
                    assertNull(connection.set(key, "after".getBytes(StandardCharsets.UTF_8)));
                    return new String(connection.get(key), StandardCharsets.UTF_8);
                });
                assertEquals("before", visible);
                for (TransactionSynchronization synchronization
                        : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                }
            } finally {
                try {
                    if (TransactionSynchronizationManager.hasResource(redis.factory)) {
                        for (TransactionSynchronization synchronization
                                : TransactionSynchronizationManager.getSynchronizations()) {
                            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                        }
                    }
                } finally {
                    TransactionSynchronizationManager.clear();
                }
            }
            assertEquals("after", redis.template().opsForValue().get("transaction-key"));
        }
    }
}
