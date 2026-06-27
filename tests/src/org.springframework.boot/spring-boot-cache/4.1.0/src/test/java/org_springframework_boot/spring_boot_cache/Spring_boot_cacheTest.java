/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizers;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class Spring_boot_cacheTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
            .withUserConfiguration(CachingConfiguration.class);

    @Test
    void cachePropertiesExposeCommonAndProviderSpecificSettings() {
        CacheProperties properties = new CacheProperties();
        Resource infinispanConfig = new ByteArrayResource("<infinispan />".getBytes(StandardCharsets.UTF_8));
        Resource jcacheConfig = new ByteArrayResource("jcache".getBytes(StandardCharsets.UTF_8));

        properties.setType(CacheType.SIMPLE);
        properties.setCacheNames(List.of("books", "authors"));
        properties.getCaffeine().setSpec("maximumSize=100,expireAfterWrite=10m");
        properties.getCouchbase().setExpiration(Duration.ofMinutes(30));
        properties.getInfinispan().setConfig(infinispanConfig);
        properties.getJcache().setProvider("com.example.TestCachingProvider");
        properties.getJcache().setConfig(jcacheConfig);
        properties.getRedis().setTimeToLive(Duration.ofHours(1));
        properties.getRedis().setCacheNullValues(false);
        properties.getRedis().setKeyPrefix("test::");
        properties.getRedis().setUseKeyPrefix(false);
        properties.getRedis().setEnableStatistics(true);

        assertThat(properties.getType()).isEqualTo(CacheType.SIMPLE);
        assertThat(properties.getCacheNames()).containsExactly("books", "authors");
        assertThat(properties.getCaffeine().getSpec()).isEqualTo("maximumSize=100,expireAfterWrite=10m");
        assertThat(properties.getCouchbase().getExpiration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.getInfinispan().getConfig()).isSameAs(infinispanConfig);
        assertThat(properties.getJcache().getProvider()).isEqualTo("com.example.TestCachingProvider");
        assertThat(properties.getJcache().getConfig()).isSameAs(jcacheConfig);
        assertThat(properties.getRedis().getTimeToLive()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getRedis().isCacheNullValues()).isFalse();
        assertThat(properties.getRedis().getKeyPrefix()).isEqualTo("test::");
        assertThat(properties.getRedis().isUseKeyPrefix()).isFalse();
        assertThat(properties.getRedis().isEnableStatistics()).isTrue();
        assertThat(properties.resolveConfigLocation(infinispanConfig)).isSameAs(infinispanConfig);
        assertThat(properties.resolveConfigLocation(null)).isNull();

        Resource missingConfig = new ByteArrayResource(new byte[0]) {
            @Override
            public boolean exists() {
                return false;
            }
        };
        assertThatIllegalArgumentException().isThrownBy(() -> properties.resolveConfigLocation(missingConfig))
            .withMessageContaining("must exist");
    }

    @Test
    void cacheManagerCustomizersApplyOnlyToSupportedCacheManagerTypes() {
        List<String> invocations = new ArrayList<>();
        CacheManagerCustomizer<ConcurrentMapCacheManager> concurrentCustomizer =
                new CacheManagerCustomizer<ConcurrentMapCacheManager>() {
                    @Override
                    public void customize(ConcurrentMapCacheManager cacheManager) {
                        invocations.add("concurrent");
                        cacheManager.setAllowNullValues(false);
                        cacheManager.setCacheNames(List.of("customized"));
                    }
                };
        CacheManagerCustomizer<NoOpCacheManager> noOpCustomizer = new CacheManagerCustomizer<NoOpCacheManager>() {
            @Override
            public void customize(NoOpCacheManager cacheManager) {
                invocations.add("noop");
            }
        };
        CacheManagerCustomizers customizers =
                new CacheManagerCustomizers(List.of(concurrentCustomizer, noOpCustomizer));

        ConcurrentMapCacheManager concurrentManager = customizers.customize(new ConcurrentMapCacheManager());
        NoOpCacheManager noOpManager = customizers.customize(new NoOpCacheManager());

        assertThat(concurrentManager.isAllowNullValues()).isFalse();
        assertThat(concurrentManager.getCacheNames()).containsExactly("customized");
        assertThat(noOpManager.getCache("anything")).isNotNull();
        assertThat(invocations).containsExactly("concurrent", "noop");
    }

    @Test
    void autoConfigurationIsAdvertisedForSpringBootDiscovery() {
        ImportCandidates candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader());

        assertThat(candidates.getCandidates()).contains(CacheAutoConfiguration.class.getName());
    }

    @Test
    void simpleCacheAutoConfigurationCreatesNamedConcurrentMapCachesAndAppliesCustomizers() {
        this.contextRunner
            .withUserConfiguration(ConcurrentMapCustomizerConfiguration.class)
            .withPropertyValues("spring.cache.type=simple", "spring.cache.cache-names=books,authors")
            .run((context) -> {
                assertThat(context).hasSingleBean(CacheManager.class);
                assertThat(context).hasSingleBean(CacheProperties.class);
                assertThat(context).hasSingleBean(CacheManagerCustomizers.class);
                CacheManager cacheManager = context.getBean(CacheManager.class);

                assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
                ConcurrentMapCacheManager concurrentMapCacheManager = (ConcurrentMapCacheManager) cacheManager;
                assertThat(concurrentMapCacheManager.getCacheNames()).containsExactlyInAnyOrder("books", "authors");
                assertThat(concurrentMapCacheManager.isAllowNullValues()).isFalse();

                Cache books = concurrentMapCacheManager.getCache("books");
                assertThat(books).isNotNull();
                books.put("isbn", "Spring Boot in Action");
                assertThat(books.get("isbn", String.class)).isEqualTo("Spring Boot in Action");
                assertThat(concurrentMapCacheManager.getCache("dynamic")).isNull();
            });
    }

    @Test
    void genericCacheAutoConfigurationAdaptsUserProvidedCacheBeans() {
        this.contextRunner.withUserConfiguration(GenericCacheConfiguration.class).run((context) -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);

            assertThat(cacheManager).isInstanceOf(SimpleCacheManager.class);
            assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("primary", "secondary");
            Cache primary = cacheManager.getCache("primary");
            Cache secondary = cacheManager.getCache("secondary");
            assertThat(primary).isNotNull();
            assertThat(secondary).isNotNull();
            primary.put("one", 1);
            secondary.put("two", 2);
            assertThat(primary.get("one", Integer.class)).isEqualTo(1);
            assertThat(secondary.get("two", Integer.class)).isEqualTo(2);
            assertThat(cacheManager.getCache("missing")).isNull();
        });
    }

    @Test
    void noOpCacheAutoConfigurationDisablesStorageButStillReturnsCachesByName() {
        this.contextRunner.withPropertyValues("spring.cache.type=none").run((context) -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);

            assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
            Cache cache = cacheManager.getCache("ignored");
            assertThat(cache).isNotNull();
            cache.put("key", "value");
            assertThat(cache.get("key")).isNull();
            assertThat(cacheManager.getCacheNames()).contains("ignored");
        });
    }

    @Test
    void autoConfigurationBacksOffWhenUserProvidesCacheManager() {
        this.contextRunner.withUserConfiguration(UserCacheManagerConfiguration.class).run((context) -> {
            assertThat(context).hasSingleBean(CacheManager.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);

            assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
            assertThat(cacheManager.getCacheNames()).containsExactly("manual");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    static class ConcurrentMapCustomizerConfiguration {

        @Bean
        CacheManagerCustomizer<ConcurrentMapCacheManager> concurrentMapCacheManagerCustomizer() {
            return new CacheManagerCustomizer<ConcurrentMapCacheManager>() {
                @Override
                public void customize(ConcurrentMapCacheManager cacheManager) {
                    cacheManager.setAllowNullValues(false);
                }
            };
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class GenericCacheConfiguration {

        @Bean
        Cache primaryCache() {
            return new ConcurrentMapCache("primary");
        }

        @Bean
        Cache secondaryCache() {
            return new ConcurrentMapCache("secondary");
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class UserCacheManagerConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("manual");
        }

    }

}
