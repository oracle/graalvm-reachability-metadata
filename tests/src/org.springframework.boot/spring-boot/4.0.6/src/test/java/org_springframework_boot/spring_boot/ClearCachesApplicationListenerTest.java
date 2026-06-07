/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class ClearCachesApplicationListenerTest {

    @Test
    void runClearsContextClassLoaderCachesWhenContextIsRefreshed() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        CacheClearingClassLoader cacheClearingClassLoader = new CacheClearingClassLoader(originalClassLoader);
        Thread.currentThread().setContextClassLoader(cacheClearingClassLoader);
        try (ConfigurableApplicationContext context = createApplication().run()) {
            assertThat(context.isActive()).isTrue();
            assertThat(cacheClearingClassLoader.getClearCacheCount()).isEqualTo(1);
        }
        finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private SpringApplication createApplication() {
        SpringApplication application = new SpringApplication(CacheClearingSpringBootApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application;
    }

    public static final class CacheClearingClassLoader extends ClassLoader {

        private final AtomicInteger clearCacheCount = new AtomicInteger();

        CacheClearingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public void clearCache() {
            this.clearCacheCount.incrementAndGet();
        }

        int getClearCacheCount() {
            return this.clearCacheCount.get();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class CacheClearingSpringBootApplication {

    }

}
