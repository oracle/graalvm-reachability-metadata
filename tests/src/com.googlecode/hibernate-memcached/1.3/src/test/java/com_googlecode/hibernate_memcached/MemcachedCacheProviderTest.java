/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode.hibernate_memcached;

import com.googlecode.hibernate.memcached.Config;
import com.googlecode.hibernate.memcached.HashCodeKeyStrategy;
import com.googlecode.hibernate.memcached.Md5KeyStrategy;
import com.googlecode.hibernate.memcached.Memcache;
import com.googlecode.hibernate.memcached.MemcacheClientFactory;
import com.googlecode.hibernate.memcached.MemcachedCache;
import com.googlecode.hibernate.memcached.MemcachedCacheProvider;
import com.googlecode.hibernate.memcached.PropertiesHelper;
import com.googlecode.hibernate.memcached.Sha1KeyStrategy;
import com.googlecode.hibernate.memcached.StringKeyStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import net.spy.memcached.HashAlgorithm;
import org.hibernate.cache.Cache;
import org.hibernate.cache.CacheException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MemcachedCacheProviderTest {

    private static final String FACTORY_PROPERTY = Config.PROP_MEMCACHE_CLIENT_FACTORY;
    private static final String KEY_STRATEGY_PROPERTY = "hibernate.memcached.keyStrategy";

    @ParameterizedTest
    @MethodSource("keyStrategies")
    void instantiatesConfiguredKeyStrategiesAndUsesThemForCacheOperations(
            Class<?> strategyClass, int expectedKeyLength) {
        Properties properties = inMemoryProperties();
        properties.setProperty(KEY_STRATEGY_PROPERTY, strategyClass.getName());
        MemcachedCacheProvider provider = startProvider(properties);

        try {
            MemcachedCache cache = (MemcachedCache) provider.buildCache("sample region", properties);

            cache.put("customer 42", "initial");
            assertThat(cache.get("customer 42")).isEqualTo("initial");
            cache.update("customer 42", "updated");
            assertThat(cache.read("customer 42")).isEqualTo("updated");

            String storedKey = RecordingMemcacheFactory.client.onlyValueKey();
            assertThat(storedKey).doesNotContain(" ").hasSize(expectedKeyLength);
            assertThat(cache.getKeyStrategy()).isInstanceOf(strategyClass);

            cache.remove("customer 42");
            assertThat(cache.get("customer 42")).isNull();
        } finally {
            provider.stop();
        }

        assertThat(RecordingMemcacheFactory.client.shutdown).isTrue();
    }

    static Stream<Arguments> keyStrategies() {
        return Stream.of(
                Arguments.of(HashCodeKeyStrategy.class, 26),
                Arguments.of(Md5KeyStrategy.class, 32),
                Arguments.of(Sha1KeyStrategy.class, 40),
                Arguments.of(StringKeyStrategy.class, 25));
    }

    @Test
    void isolatesRegionsAndClearsOnlyTheSelectedNamespace() {
        Properties properties = inMemoryProperties();
        properties.setProperty(KEY_STRATEGY_PROPERTY, StringKeyStrategy.class.getName());
        properties.setProperty(Config.PROP_CLEAR_SUPPORTED, "true");
        MemcachedCacheProvider provider = startProvider(properties);

        try {
            Cache customers = provider.buildCache("customers", properties);
            Cache orders = provider.buildCache("orders", properties);
            customers.put("shared", "customer-value");
            orders.put("shared", "order-value");

            assertThat(customers.get("shared")).isEqualTo("customer-value");
            assertThat(orders.get("shared")).isEqualTo("order-value");
            assertThat(RecordingMemcacheFactory.client.values)
                    .containsKeys("customers:0:shared", "orders:0:shared");

            customers.clear();

            assertThat(customers.get("shared")).isNull();
            assertThat(orders.get("shared")).isEqualTo("order-value");
            customers.put("shared", "new-customer-value");
            assertThat(RecordingMemcacheFactory.client.values)
                    .containsEntry("customers:1:shared", "new-customer-value");
        } finally {
            provider.stop();
        }
    }

    @Test
    void appliesRegionOverridesAndDogpilePrevention() {
        Properties properties = inMemoryProperties();
        properties.setProperty(KEY_STRATEGY_PROPERTY, StringKeyStrategy.class.getName());
        properties.setProperty(Config.PROP_CACHE_TIME_SECONDS, "40");
        properties.setProperty(Config.PROP_DOGPILE_PREVENTION, "false");
        properties.setProperty("hibernate.memcached.products.cacheTimeSeconds", "12");
        properties.setProperty("hibernate.memcached.products.dogpilePrevention", "true");
        properties.setProperty("hibernate.memcached.products.dogpilePrevention.expirationFactor", "2.5");
        MemcachedCacheProvider provider = startProvider(properties);

        try {
            MemcachedCache cache = (MemcachedCache) provider.buildCache("products", properties);
            String valueKey = "products:0:sku-1";
            String tokenKey = valueKey + ".dogpileTokenKey";

            assertThat(cache.get("sku-1")).isNull();
            assertThat(RecordingMemcacheFactory.client.values)
                    .containsEntry(tokenKey, MemcachedCache.DOGPILE_TOKEN);
            assertThat(RecordingMemcacheFactory.client.expirations).containsEntry(tokenKey, 12);

            cache.put("sku-1", "available");

            assertThat(cache.get("sku-1")).isEqualTo("available");
            assertThat(RecordingMemcacheFactory.client.expirations)
                    .containsEntry(valueKey, 30)
                    .containsEntry(tokenKey, 12);
            assertThat(cache.getCacheTimeSeconds()).isEqualTo(12);
            assertThat(cache.isDogpilePreventionEnabled()).isTrue();
            assertThat(cache.getDogpilePreventionExpirationFactor()).isEqualTo(2.5);
        } finally {
            provider.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DefaultConnectionFactory",
            "KetamaConnectionFactory",
            "BinaryConnectionFactory"
    })
    void startsDefaultSpyFactoryWithSupportedConnectionFactories(String connectionFactory) {
        Properties properties = new Properties();
        properties.setProperty("hibernate.memcached.servers", "localhost:11211");
        properties.setProperty("hibernate.memcached.connectionFactory", connectionFactory);
        properties.setProperty("hibernate.memcached.hashAlgorithm", HashAlgorithm.KETAMA_HASH.name());
        properties.setProperty("hibernate.memcached.daemonMode", "true");
        MemcachedCacheProvider provider = new MemcachedCacheProvider();

        try {
            provider.start(properties);
            Cache cache = provider.buildCache("spy-" + connectionFactory, properties);

            assertThat(cache.getRegionName()).isEqualTo("spy-" + connectionFactory);
            assertThat(cache).isInstanceOf(MemcachedCache.class);
        } finally {
            provider.stop();
        }
    }

    @Test
    void startsSpyFactoryWithPlainAuthentication() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.memcached.servers", "localhost:11211");
        properties.setProperty("hibernate.memcached.daemonMode", "true");
        properties.setProperty("hibernate.memcached.username", "cache-user");
        properties.setProperty("hibernate.memcached.password", "cache-password");
        MemcachedCacheProvider provider = new MemcachedCacheProvider();

        try {
            provider.start(properties);
            Cache cache = provider.buildCache("authenticated", properties);

            assertThat(cache.getRegionName()).isEqualTo("authenticated");
            assertThat(cache).isInstanceOf(MemcachedCache.class);
        } finally {
            provider.stop();
        }
    }

    @Test
    void usesPublicPropertiesConstructorOnCustomFactory() {
        Properties properties = inMemoryProperties();
        properties.setProperty("custom.setting", "constructor-value");
        MemcachedCacheProvider provider = startProvider(properties);

        try {
            assertThat(RecordingMemcacheFactory.properties.get("custom.setting"))
                    .isEqualTo("constructor-value");
            assertThat(provider.isMinimalPutsEnabledByDefault()).isTrue();
            assertThat(provider.nextTimestamp()).isPositive();
        } finally {
            provider.stop();
        }
    }

    @Test
    void wrapsFactoryInitializationExceptionsAsCacheExceptions() {
        Properties properties = new Properties();
        properties.setProperty(FACTORY_PROPERTY, FailingMemcacheFactory.class.getName());
        MemcachedCacheProvider provider = new MemcachedCacheProvider();

        assertThatThrownBy(() -> provider.start(properties))
                .isInstanceOf(CacheException.class)
                .hasMessage("Unable to initialize MemcachedClient")
                .hasRootCauseMessage("deliberate factory failure");
    }

    @Test
    void rejectsUnsupportedClearIndexValues() {
        Properties properties = inMemoryProperties();
        properties.setProperty(KEY_STRATEGY_PROPERTY, StringKeyStrategy.class.getName());
        properties.setProperty(Config.PROP_CLEAR_SUPPORTED, "true");
        MemcachedCacheProvider provider = startProvider(properties);

        try {
            Cache cache = provider.buildCache("customers", properties);
            RecordingMemcacheFactory.client.values.put("customers:index_key", Integer.valueOf(3));

            assertThatThrownBy(() -> cache.get("customer-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported type")
                    .hasMessageContaining("customers:index_key");
        } finally {
            provider.stop();
        }
    }

    @Test
    void rejectsInvalidDogpileExpirationFactor() {
        Properties properties = inMemoryProperties();
        properties.setProperty("hibernate.memcached.inventory.dogpilePrevention", "true");
        properties.setProperty("hibernate.memcached.inventory.dogpilePrevention.expirationFactor", "0.5");
        MemcachedCacheProvider provider = startProvider(properties);

        try {
            assertThatThrownBy(() -> provider.buildCache("inventory", properties))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be greater than 1.0");
        } finally {
            provider.stop();
        }
    }

    private static Properties inMemoryProperties() {
        Properties properties = new Properties();
        properties.setProperty(FACTORY_PROPERTY, RecordingMemcacheFactory.class.getName());
        return properties;
    }

    private static MemcachedCacheProvider startProvider(Properties properties) {
        RecordingMemcacheFactory.reset();
        MemcachedCacheProvider provider = new MemcachedCacheProvider();
        provider.start(properties);
        return provider;
    }

    public static final class RecordingMemcacheFactory implements MemcacheClientFactory {
        private static PropertiesHelper properties;
        private static InMemoryMemcache client;

        public RecordingMemcacheFactory(PropertiesHelper properties) {
            RecordingMemcacheFactory.properties = properties;
        }

        static void reset() {
            properties = null;
            client = null;
        }

        @Override
        public Memcache createMemcacheClient() {
            client = new InMemoryMemcache();
            return client;
        }
    }

    public static final class FailingMemcacheFactory implements MemcacheClientFactory {

        public FailingMemcacheFactory(PropertiesHelper properties) {
        }

        @Override
        public Memcache createMemcacheClient() throws Exception {
            throw new Exception("deliberate factory failure");
        }
    }

    private static final class InMemoryMemcache implements Memcache {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, Integer> expirations = new HashMap<>();
        private boolean shutdown;

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public Map<String, Object> getMulti(String... keys) {
            Map<String, Object> result = new HashMap<>();
            for (String key : keys) {
                if (values.containsKey(key)) {
                    result.put(key, values.get(key));
                }
            }
            return result;
        }

        @Override
        public void set(String key, int cacheTimeSeconds, Object value) {
            values.put(key, value);
            expirations.put(key, cacheTimeSeconds);
        }

        @Override
        public void delete(String key) {
            values.remove(key);
            expirations.remove(key);
        }

        @Override
        public void incr(String key, int factor, int startingValue) {
            Object current = values.get(key);
            long next = current == null ? startingValue : ((Number) current).longValue() + factor;
            values.put(key, next);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        String onlyValueKey() {
            return values.keySet().stream()
                    .filter(key -> !key.endsWith(":index_key"))
                    .findFirst()
                    .orElseThrow(AssertionError::new);
        }
    }
}
