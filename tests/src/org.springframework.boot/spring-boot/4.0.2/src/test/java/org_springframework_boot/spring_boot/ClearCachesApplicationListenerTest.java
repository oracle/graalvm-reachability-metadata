/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ClearCachesApplicationListenerTest {

    @Test
    void clearsDelegatingClassLoaderCachesWhenTheContextIsRefreshed() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        CacheTrackingClassLoader loader = new CacheTrackingClassLoader(original);
        SpringApplication application = new SpringApplication();
        ApplicationListener<?> listener = application.getListeners().stream()
                .filter(candidate -> candidate.getClass().getSimpleName().equals("ClearCachesApplicationListener"))
                .findFirst().orElseThrow();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.addApplicationListener(listener);
            context.registerBean(StringBuilder.class, () -> new StringBuilder("available"));
            Thread.currentThread().setContextClassLoader(loader);
            context.refresh();

            assertThat(loader.clearCount).isEqualTo(1);
            assertThat(context.getBean(StringBuilder.class).toString()).isEqualTo("available");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    public static class CacheTrackingClassLoader extends ClassLoader {
        private int clearCount;

        public CacheTrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public void clearCache() {
            clearCount++;
        }
    }
}
