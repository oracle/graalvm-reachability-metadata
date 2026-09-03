/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache_core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.util.ClassLoaderUtil;

import org.junit.jupiter.api.Test;

public class ClassLoaderUtilTest {
    @Test
    void loadsEhcacheClassWithStandardClassLoader() throws ClassNotFoundException {
        Class<?> loadedClass = ClassLoaderUtil.loadClass(CacheConfiguration.class.getName());

        assertThat(loadedClass).isSameAs(CacheConfiguration.class);
    }

    @Test
    void loadsEhcacheClassWithFallbackClassLoader() throws ClassNotFoundException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            Class<?> loadedClass = ClassLoaderUtil.loadClass("net.sf.ehcache.config.CacheConfiguration");

            assertThat(loadedClass).isSameAs(CacheConfiguration.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createsEhcacheInstanceWithNoArgumentConstructor() {
        Object instance = ClassLoaderUtil.createNewInstance(CacheConfiguration.class.getName());

        assertThat(instance).isInstanceOf(CacheConfiguration.class);
    }

    @Test
    void createsEhcacheInstanceWithPublicConstructorArguments() {
        Object instance = ClassLoaderUtil.createNewInstance(
                CacheConfiguration.class.getName(),
                new Class[] {String.class, int.class},
                new Object[] {"configured-cache", 128});

        assertThat(instance).isInstanceOf(CacheConfiguration.class);
        CacheConfiguration configuration = (CacheConfiguration) instance;
        assertThat(configuration.getName()).isEqualTo("configured-cache");
        assertThat(configuration.getMaxElementsInMemory()).isEqualTo(128);
    }
}
