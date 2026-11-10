/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.SoftCache;
import org.apache.ibatis.cache.decorators.WeakCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.mapping.CacheBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CacheBuilderTest {
    @Test
    void evictsInInsertionOrderWithFifoPolicy() {
        Cache cache = new CacheBuilder("fifo").implementation(PerpetualCache.class)
                .addDecorator(FifoCache.class).size(2).build();
        cache.putObject("first", "Ada");
        cache.putObject("second", "Grace");
        assertEquals("Ada", cache.getObject("first"));
        cache.putObject("third", "Edsger");
        assertNull(cache.getObject("first"));
        assertEquals("Grace", cache.getObject("second"));
        assertEquals("Edsger", cache.getObject("third"));
        assertEquals(2, cache.getSize());
    }

    @Test
    void storesAndClearsValuesWithSoftReferences() {
        Cache cache = new CacheBuilder("soft").implementation(PerpetualCache.class)
                .addDecorator(SoftCache.class).build();
        cache.putObject("name", "Ada");
        assertEquals("Ada", cache.getObject("name"));
        cache.clear();
        assertNull(cache.getObject("name"));
        assertEquals(0, cache.getSize());
    }

    @Test
    void storesAndRemovesValuesWithWeakReferences() {
        Cache cache = new CacheBuilder("weak").implementation(PerpetualCache.class)
                .addDecorator(WeakCache.class).build();
        cache.putObject("name", "Grace");
        assertEquals("Grace", cache.getObject("name"));
        cache.removeObject("name");
        assertNull(cache.getObject("name"));
        assertEquals(0, cache.getSize());
    }

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
