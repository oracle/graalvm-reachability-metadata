/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentConverterTest {

    @Test
    void convertsThePreparedEnvironmentToTheContextFactorysRequestedType() {
        TestApplication application = new TestApplication(ApplicationSource.class);
        AtomicReference<ConfigurableEnvironment> original = new AtomicReference<>();
        application.addListeners((ApplicationEnvironmentPreparedEvent event) -> original.set(event.getEnvironment()));
        application.setDefaultProperties(Map.of("deployment.region", "north"));
        application.setAdditionalProfiles("production");
        application.setApplicationContextFactory(new ApplicationContextFactory() {
            @Override
            public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType webApplicationType) {
                return DeploymentEnvironment.class;
            }

            @Override
            public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
                return new GenericApplicationContext();
            }
        });

        try (ConfigurableApplicationContext context = application.run("--deployment.zone=blue")) {
            ConfigurableEnvironment converted = context.getEnvironment();
            assertThat(converted).isInstanceOf(DeploymentEnvironment.class).isNotSameAs(original.get());
            assertThat(converted.getActiveProfiles()).contains("production");
            assertThat(converted.getProperty("deployment.region")).isEqualTo("north");
            assertThat(converted.getProperty("deployment.zone")).isEqualTo("blue");
            assertThat(converted.getConversionService()).isSameAs(original.get().getConversionService());
        }
    }

    public static class DeploymentEnvironment extends StandardEnvironment {
    }

    public static class ApplicationSource {
    }
}
