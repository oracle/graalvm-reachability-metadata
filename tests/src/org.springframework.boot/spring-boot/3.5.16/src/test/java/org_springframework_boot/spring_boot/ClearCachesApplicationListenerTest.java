/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ClearCachesApplicationListenerTest {

    @Test
    void clearsLoaderCachesWhenApplicationContextRefreshes() {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        CacheTrackingClassLoader loader = new CacheTrackingClassLoader(original);
        SpringApplication application = new SpringApplication(ApplicationSource.class);
        application.setMainApplicationClass(ApplicationSource.class);
        application.setApplicationContextFactory(ApplicationContextFactory.of(GenericApplicationContext::new));
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.addInitializers((AotApplicationContextInitializer<GenericApplicationContext>) context ->
                context.getBeanFactory().registerSingleton("cacheStatus", "ready"));

        try {
            thread.setContextClassLoader(loader);
            try (ConfigurableApplicationContext context = application.run()) {
                assertThat(context.getBean("cacheStatus")).isEqualTo("ready");
                assertThat(loader.getClearCount()).isEqualTo(1);
            }
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    public static class CacheTrackingClassLoader extends ClassLoader {

        private int clearCount;

        public CacheTrackingClassLoader(ClassLoader parent) {
            super(parent);
        }

        public void clearCache() {
            this.clearCount++;
        }

        public int getClearCount() {
            return this.clearCount;
        }
    }

    public static class ApplicationSource {
    }
}
