/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentConverterTest {
    @Test
    void convertsEnvironmentAfterCommandLineChangesApplicationType() {
        InitialEnvironment source = new InitialEnvironment();
        source.setActiveProfiles("production");
        source.getPropertySources().addFirst(new MapPropertySource("deployment", Map.of("region", "west")));
        SpringApplication application = new SpringApplication(EnvironmentConverterTest.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.setListeners(List.of());
        application.setApplicationContextFactory(new ApplicationContextFactory() {
            @Override
            public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType type) {
                return type == WebApplicationType.NONE ? StandardEnvironment.class : InitialEnvironment.class;
            }

            @Override
            public ConfigurableEnvironment createEnvironment(WebApplicationType type) {
                return source;
            }

            @Override
            public ConfigurableApplicationContext create(WebApplicationType type) {
                return new GenericApplicationContext();
            }
        });
        application.addInitializers((AotApplicationContextInitializer<GenericApplicationContext>) context ->
                context.registerBean("consumerMarker", String.class, () -> "ready"));

        try (ConfigurableApplicationContext context = application.run("--spring.main.web-application-type=none")) {
            ConfigurableEnvironment converted = context.getEnvironment();

            assertThat(converted).isExactlyInstanceOf(StandardEnvironment.class).isNotSameAs(source);
            assertThat(converted.getActiveProfiles()).containsExactly("production");
            assertThat(converted.getProperty("region")).isEqualTo("west");
            assertThat(converted.getConversionService()).isSameAs(source.getConversionService());
            assertThat(context.getBean("consumerMarker")).isEqualTo("ready");
        }
    }

    public static class InitialEnvironment extends StandardEnvironment {
    }
}
