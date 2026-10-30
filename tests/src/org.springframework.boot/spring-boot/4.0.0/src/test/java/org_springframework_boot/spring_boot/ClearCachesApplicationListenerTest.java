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
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class ClearCachesApplicationListenerTest {
    @Test
    @SuppressWarnings("unchecked")
    void clearsResourceCachesOnTheContextClassLoaderAfterRefresh() {
        ApplicationListener<ContextRefreshedEvent> listener = SpringFactoriesLoader
                .forDefaultResourceLocation(getClass().getClassLoader()).load(ApplicationListener.class).stream()
                .filter(candidate -> candidate.getClass().getSimpleName().equals("ClearCachesApplicationListener"))
                .findFirst().orElseThrow();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        CachingClassLoader loader = new CachingClassLoader(previous);
        assertThat(loader.getResource("META-INF/spring.factories")).isNotNull();
        assertThat(loader.cachedResources()).isEqualTo(1);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            Thread.currentThread().setContextClassLoader(loader);
            context.addApplicationListener(listener);
            context.refresh();

            assertThat(loader.cachedResources()).isZero();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public static class CachingClassLoader extends ClassLoader {
        private final Map<String, URL> resources = new HashMap<>();

        public CachingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            return resources.computeIfAbsent(name, super::getResource);
        }

        public void clearCache() {
            resources.clear();
        }

        public int cachedResources() {
            return resources.size();
        }
    }
}
