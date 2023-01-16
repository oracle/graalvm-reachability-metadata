/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api.core.stepbystep;

import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import javax.cache.spi.CachingProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTest {
    @Test
    void testSimple() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager manager = provider.getCacheManager();
        MutableConfiguration<String, String> configuration = new MutableConfiguration<String, String>()
                .setStoreByValue(false).setTypes(String.class, String.class);
        Cache<String, String> cache = manager.createCache("my-cache", configuration);
        CacheEntryListenerConfiguration<String, String> listenerConfiguration = new MutableCacheEntryListenerConfiguration<>(
                FactoryBuilder.factoryOf(MyCacheEntryListener.class),
                null, false, true
        );
        cache.registerCacheEntryListener(listenerConfiguration);
        cache.put("message", "hello");
        cache.put("message", "g'day");
        cache.put("message", "bonjour");
        String result = cache.invoke("message", new AbstractEntryProcessor<>() {
            @Override
            public String process(MutableEntry<String, String> entry,
                                  Object... arguments) throws EntryProcessorException {
                return entry.exists() ? entry.getValue().toUpperCase() : null;
            }
        });
        assertThat(result).isEqualTo("BONJOUR");
        assertThat(cache.get("message")).isEqualTo("bonjour");
    }
}
