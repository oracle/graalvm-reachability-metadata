/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api.core;

import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class EntryProcessorTest {
    @Test
    public void incrementValue() {
        CacheManager manager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> configuration = new MutableConfiguration<String, Integer>().setTypes(String.class, Integer.class);
        Cache<String, Integer> cache = manager.createCache("example", configuration);
        String key = "counter";
        cache.put(key, 1);
        assertThat(cache.invoke(key, new IncrementProcessor<>())).isEqualTo(1);
        assertThat(cache.get(key)).isEqualTo(2);
    }

    public static class IncrementProcessor<K> implements EntryProcessor<K, Integer, Integer>, Serializable {
        public static final long serialVersionUID = 201306211238L;

        @Override
        public Integer process(MutableEntry<K, Integer> entry, Object... arguments) {
            if (entry.exists()) {
                Integer current = entry.getValue();
                entry.setValue(current + 1);
                return current;
            } else {
                entry.setValue(0);
                return -1;
            }
        }
    }
}
