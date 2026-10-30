/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.ibatis.sqlmap.engine.cache.CacheKey;
import com.ibatis.sqlmap.engine.cache.CacheModel;
import com.ibatis.sqlmap.engine.cache.lru.LruCacheController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class CacheModelTest {
    @Test
    void serializedCacheReturnsIndependentCopiesAndCanBeFlushed() throws Exception {
        CacheModel cache = new CacheModel();
        cache.setId("editable-results");
        cache.setReadOnly(false);
        cache.setSerialize(true);
        cache.setCacheController(new LruCacheController());
        cache.configure(new Properties());
        CacheKey key = new CacheKey();
        key.update("names");
        ArrayList<String> original = new ArrayList<>(List.of("alpha", "beta"));
        cache.putObject(key, original);
        original.add("outside-cache");

        Object first = cache.getObject(key);
        assertThat(first).isInstanceOf(ArrayList.class).isNotSameAs(original);
        assertThat(first).isEqualTo(List.of("alpha", "beta"));
        ((List<?>) first).clear();
        Object second = cache.getObject(key);
        assertThat(second).isNotSameAs(first);
        assertThat(second).isEqualTo(List.of("alpha", "beta"));
        assertThat(cache.getHitRatio()).isEqualTo(1.0);

        cache.flush();
        assertThat(cache.getObject(key)).isNull();
    }
}
