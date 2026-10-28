/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.mapping.CacheBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CacheBuilderTest {
    @Test
    void constructsCacheAndEvictingDecorator() {
        Cache cache = new CacheBuilder("bounded").implementation(PerpetualCache.class)
                .addDecorator(LruCache.class).size(1).build();
        cache.putObject("old", "first");
        cache.putObject("new", "second");
        assertNull(cache.getObject("old"));
        assertEquals("second", cache.getObject("new"));
        assertEquals(1, cache.getSize());
    }
}
