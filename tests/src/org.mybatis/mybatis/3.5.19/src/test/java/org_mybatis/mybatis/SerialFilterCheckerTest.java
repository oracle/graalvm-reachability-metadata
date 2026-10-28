/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SerialFilterCheckerTest {
    @Test
    void checksSerializationFilterDuringCacheReads() {
        SerializedCache cache = new SerializedCache(new PerpetualCache("filter"));
        cache.putObject("numbers", new int[]{2, 5});
        assertArrayEquals(new int[]{2, 5}, (int[]) cache.getObject("numbers"));
        assertArrayEquals(new int[]{2, 5}, (int[]) cache.getObject("numbers"));
    }
}
