/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SerializedCacheTest {
    @Test
    void returnsIndependentSerializedCopies() {
        Cache cache = new SerializedCache(new PerpetualCache("copies"));
        List<String> original = new ArrayList<>(List.of("Ada"));
        cache.putObject("user", original);
        original.add("Grace");
        Object restored = cache.getObject("user");
        assertEquals(List.of("Ada"), restored);
        assertNotSame(original, restored);
        assertNotSame(restored, cache.getObject("user"));
    }
}
