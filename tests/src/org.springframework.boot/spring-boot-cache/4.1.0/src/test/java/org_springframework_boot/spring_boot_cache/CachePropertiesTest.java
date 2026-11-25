/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_cache;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CachePropertiesTest {
    @Test
    void bindsCacheSelectionAndProviderSettingsFromApplicationProperties() {
        Map<String, String> properties = Map.of(
                "spring.cache.type", "caffeine",
                "spring.cache.cache-names", "products,prices",
                "spring.cache.caffeine.spec", "maximumSize=25,recordStats",
                "spring.cache.redis.time-to-live", "45s",
                "spring.cache.redis.cache-null-values", "false",
                "spring.cache.redis.key-prefix", "catalog:",
                "spring.cache.redis.use-key-prefix", "false",
                "spring.cache.redis.enable-statistics", "true",
                "spring.cache.couchbase.expiration", "2m");
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));

        CacheProperties bound = binder.bind("spring.cache", CacheProperties.class).get();

        assertThat(bound.getType()).isEqualTo(CacheType.CAFFEINE);
        assertThat(bound.getCacheNames()).containsExactly("products", "prices");
        assertThat(bound.getCaffeine().getSpec()).isEqualTo("maximumSize=25,recordStats");
        assertThat(bound.getRedis().getTimeToLive()).isEqualTo(Duration.ofSeconds(45));
        assertThat(bound.getRedis().isCacheNullValues()).isFalse();
        assertThat(bound.getRedis().getKeyPrefix()).isEqualTo("catalog:");
        assertThat(bound.getRedis().isUseKeyPrefix()).isFalse();
        assertThat(bound.getRedis().isEnableStatistics()).isTrue();
        assertThat(bound.getCouchbase().getExpiration()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void resolvesExistingConfigurationResourcesAndRejectsMissingOnes() {
        CacheProperties properties = new CacheProperties();
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});

        assertThat(properties.resolveConfigLocation(resource)).isSameAs(resource);
        assertThat(properties.resolveConfigLocation(null)).isNull();
        assertThrows(IllegalArgumentException.class,
                () -> properties.resolveConfigLocation(new ClassPathResource("absent-cache-settings.xml")));
    }
}
