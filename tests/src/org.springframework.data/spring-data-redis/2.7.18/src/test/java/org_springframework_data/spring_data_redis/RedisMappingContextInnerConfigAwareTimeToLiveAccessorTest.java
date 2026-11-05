/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.core.mapping.RedisMappingContext;

public class RedisMappingContextInnerConfigAwareTimeToLiveAccessorTest {
    @Test
    void derivesExpirationFromAnnotatedMethodForEachWrite() {
        MappingRedisConverter converter = new MappingRedisConverter(new RedisMappingContext());
        converter.afterPropertiesSet();
        ExpiringDocument document = new ExpiringDocument();
        document.minutes = 4;
        RedisData first = new RedisData();
        converter.write(document, first);
        assertEquals(Long.valueOf(240), first.getTimeToLive());

        document.minutes = 7;
        RedisData second = new RedisData();
        converter.write(document, second);
        assertEquals(Long.valueOf(420), second.getTimeToLive());
    }

    public static class ExpiringDocument {
        public int minutes;

        public ExpiringDocument() {
        }

        @TimeToLive(unit = TimeUnit.MINUTES)
        public Long expiration() {
            return Long.valueOf(minutes);
        }
    }
}
