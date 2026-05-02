/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Properties;

import net.sf.ehcache.hibernate.EhCacheProvider;
import org.hibernate.cache.Cache;
import org.junit.jupiter.api.Test;

public class AbstractEhcacheProviderTest {
    private static final String CONFIGURATION_RESOURCE = "/abstract-ehcache-provider.xml";
    private static final String CACHE_NAME = "dynamicAccessRegion";

    @Test
    void startsProviderFromResourceResolvedByProviderClass() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(EhCacheProvider.NET_SF_EHCACHE_CONFIGURATION_RESOURCE_NAME, CONFIGURATION_RESOURCE);
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        MissingResourceClassLoader resourceClassLoader = new MissingResourceClassLoader(originalContextClassLoader);
        EhCacheProvider provider = new EhCacheProvider();

        Thread.currentThread().setContextClassLoader(resourceClassLoader);
        try {
            provider.start(properties);

            Cache cache = provider.buildCache(CACHE_NAME, properties);
            cache.put("key", "value");

            assertThat(resourceClassLoader.wasConfigurationRequested()).isTrue();
            assertThat(cache.get("key")).isEqualTo("value");
            assertThat(provider.isMinimalPutsEnabledByDefault()).isTrue();
            assertThat(provider.nextTimestamp()).isPositive();
        } finally {
            provider.stop();
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class MissingResourceClassLoader extends ClassLoader {
        private boolean configurationRequested;

        private MissingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if (CONFIGURATION_RESOURCE.equals(name)) {
                configurationRequested = true;
                return null;
            }
            return super.getResource(name);
        }

        private boolean wasConfigurationRequested() {
            return configurationRequested;
        }
    }
}
