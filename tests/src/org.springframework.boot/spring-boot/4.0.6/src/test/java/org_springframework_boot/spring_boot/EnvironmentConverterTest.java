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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentConverterTest {

    @Test
    void runConvertsDefaultEnvironmentToFactoryEnvironmentType() {
        SpringApplication application = new SpringApplication(EnvironmentConvertingSpringBootApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setApplicationContextFactory(new TestEnvironmentApplicationContextFactory());

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getEnvironment()).isInstanceOf(TestEnvironment.class);
        }
    }

    public static final class TestEnvironment extends StandardEnvironment {

        public TestEnvironment() {
        }

    }

    private static final class TestEnvironmentApplicationContextFactory implements ApplicationContextFactory {

        @Override
        public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType webApplicationType) {
            return TestEnvironment.class;
        }

        @Override
        public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
            return new AnnotationConfigApplicationContext();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class EnvironmentConvertingSpringBootApplication {

    }

}
