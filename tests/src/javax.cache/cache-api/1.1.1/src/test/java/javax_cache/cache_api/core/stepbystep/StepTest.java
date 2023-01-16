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
import javax.cache.processor.EntryProcessor;
import javax.cache.spi.CachingProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class StepTest {

    @Test
    void testStep1() {
        assertThat(Caching.getCachingProvider().getCacheManager()).isNotNull();
    }

    @Test
    void testStep2() {
        CacheManager manager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, String> configuration = new MutableConfiguration<String, String>()
                .setStoreByValue(true).setTypes(String.class, String.class);
        Cache<String, String> cache = manager.createCache("greetings2", configuration);
        cache.put("AU", "gudday mate");
        cache.put("US", "hello");
        cache.put("FR", "bonjour");
        assertThat(cache.get("AU")).isEqualTo("gudday mate");
    }

    @Test
    void testStep3() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager manager = provider.getCacheManager();
        MutableConfiguration<String, String> configuration = new MutableConfiguration<String, String>()
                .setStoreByValue(true).setTypes(String.class, String.class);
        Cache<String, String> cache = manager.createCache("greetings3", configuration);
        cache.put("AU", "gudday mate");
        cache.put("US", "hello");
        cache.put("FR", "bonjour");
        cache.invoke("AU", (entry, arguments) -> {
            if (entry.exists()) {
                String currentValue = entry.getValue();
                entry.setValue(currentValue.toUpperCase());
                return currentValue;
            } else {
                return null;
            }
        });
        assertThat(cache.get("AU")).isEqualTo("GUDDAY MATE");
    }

    @Test
    void testStep4() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager manager = provider.getCacheManager();
        MutableConfiguration<String, String> configuration = new MutableConfiguration<String, String>()
                .setStoreByValue(true).setTypes(String.class, String.class);
        Cache<String, String> cache = manager.createCache("greetings4", configuration);
        CacheEntryListenerConfiguration<String, String> listenerConfiguration = new MutableCacheEntryListenerConfiguration<>(
                FactoryBuilder.factoryOf(MyCacheEntryListener.class), null, false, true
        );
        cache.registerCacheEntryListener(listenerConfiguration);
        cache.put("AU", "gudday mate");
        cache.put("US", "hello");
        cache.put("FR", "bonjour");
        cache.invoke("AU", (EntryProcessor<String, String, String>) (entry, arguments) -> {
            if (entry.exists()) {
                entry.setValue(entry.getValue().toUpperCase());
            }
            return null;
        });
        assertThat(cache.get("AU")).isEqualTo("GUDDAY MATE");
    }
}
