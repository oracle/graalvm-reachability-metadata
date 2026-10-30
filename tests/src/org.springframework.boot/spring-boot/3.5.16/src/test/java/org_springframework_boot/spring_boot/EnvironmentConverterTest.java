/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentConverterTest {

    @Test
    void convertsStandardEnvironmentToApplicationEnvironment() {
        StandardEnvironment source = new StandardEnvironment();
        source.setActiveProfiles("cloud");
        source.getPropertySources()
                .addFirst(new MapPropertySource("deployment", Collections.singletonMap("demo.region", "eu-west")));

        SpringApplication application = new SpringApplication(ConvertingApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setApplicationContextFactory(new ApplicationContextFactory() {
            @Override
            public ConfigurableEnvironment createEnvironment(WebApplicationType webApplicationType) {
                return source;
            }

            @Override
            public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
                return new AnnotationConfigApplicationContext();
            }
        });

        try (ConfigurableApplicationContext context = application.run()) {
            ConfigurableEnvironment converted = context.getEnvironment();
            assertThat(converted).isNotSameAs(source);
            assertThat(converted.getClass()).isNotEqualTo(StandardEnvironment.class);
            assertThat(converted.getActiveProfiles()).containsExactly("cloud");
            assertThat(converted.getProperty("demo.region")).isEqualTo("eu-west");
            assertThat(converted.getPropertySources().contains("deployment")).isTrue();
            assertThat(converted.getConversionService()).isSameAs(source.getConversionService());
        }
    }

    public static class ConvertingApplication {
    }
}
