/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_redis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.core.mapping.RedisMappingContext;

public class MappingRedisConverterTest {
    @Test
    void roundTripsReferenceAndPrimitiveArrays() {
        MappingRedisConverter converter = new MappingRedisConverter(new RedisMappingContext());
        converter.afterPropertiesSet();
        ArrayDocument original = new ArrayDocument();
        original.labels = new String[] {"north", "south"};
        original.scores = new int[] {12, -3, 41};
        RedisData data = new RedisData();

        converter.write(original, data);
        ArrayDocument restored = converter.read(ArrayDocument.class, data);

        assertNotSame(original, restored);
        assertArrayEquals(original.labels, restored.labels);
        assertArrayEquals(original.scores, restored.scores);
    }

    public static class ArrayDocument {
        public String[] labels;
        public int[] scores;

        public ArrayDocument() {
        }
    }
}
