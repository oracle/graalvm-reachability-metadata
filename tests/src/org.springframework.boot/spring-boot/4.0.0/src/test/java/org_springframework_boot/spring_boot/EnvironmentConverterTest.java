/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

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
    void convertsTheInitialEnvironmentToTheContextFactorysRequiredType() {
        StandardEnvironment source = new StandardEnvironment();
        source.setActiveProfiles("deployment");
        source.getPropertySources().addFirst(new MapPropertySource("deployment", Map.of("region", "north")));
        SpringApplication application = new SpringApplication(Source.class);
        application.setMainApplicationClass(Source.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        application.setLogStartupInfo(false);
        application.setBannerMode(Banner.Mode.OFF);
        application.setApplicationContextFactory(new ApplicationContextFactory() {
            @Override
            public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType type) {
                return DeploymentEnvironment.class;
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
        AotApplicationContextInitializer<GenericApplicationContext> initializer = context ->
                context.registerBean("deploymentMessage", String.class, () -> "running");
        application.addInitializers(initializer);

        try (ConfigurableApplicationContext context = application.run()) {
            ConfigurableEnvironment converted = context.getEnvironment();
            assertThat(converted).isInstanceOf(DeploymentEnvironment.class).isNotSameAs(source);
            assertThat(converted.getProperty("region")).isEqualTo("north");
            assertThat(converted.getActiveProfiles()).containsExactly("deployment");
            assertThat(converted.getConversionService()).isSameAs(source.getConversionService());
            assertThat(context.getBean("deploymentMessage")).isEqualTo("running");
        }
    }

    public static class Source {
    }

    public static class DeploymentEnvironment extends StandardEnvironment {
    }
}
