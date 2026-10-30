/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ClearCachesApplicationListenerTest {
    @Test
    void clearsDelegatingResourceLoaderCacheAfterContextRefresh() {
        ApplicationListener<?> listener = new SpringApplication(ClearCachesApplicationListenerTest.class)
                .getListeners().stream()
                .filter(candidate -> candidate.getClass().getSimpleName().equals("ClearCachesApplicationListener"))
                .findFirst().orElseThrow();
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ResourceCachingClassLoader loader = new ResourceCachingClassLoader(original);
        assertThat(loader.cachedResource("META-INF/spring/org_springframework_boot.spring_boot.ImportMarker.imports"))
                .isNotNull();
        assertThat(loader.cacheSize()).isEqualTo(1);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            thread.setContextClassLoader(loader);
            context.addApplicationListener(listener);
            context.refresh();

            assertThat(context.isActive()).isTrue();
            assertThat(loader.cacheSize()).isZero();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    /// Delegates all class loading and caches only explicitly requested resource URLs.
    public static class ResourceCachingClassLoader extends ClassLoader {
        private final Map<String, URL> cache = new HashMap<>();

        public ResourceCachingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public URL cachedResource(String name) {
            return this.cache.computeIfAbsent(name, this::getResource);
        }

        public int cacheSize() {
            return this.cache.size();
        }

        public void clearCache() {
            this.cache.clear();
        }
    }
}
