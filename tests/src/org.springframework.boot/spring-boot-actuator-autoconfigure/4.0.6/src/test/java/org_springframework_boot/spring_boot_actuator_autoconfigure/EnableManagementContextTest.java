/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class EnableManagementContextTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ManagementContextAutoConfiguration.class,
                    InfoEndpointAutoConfiguration.class))
            .withPropertyValues("management.endpoints.web.exposure.include=info", "local.server.port=43123");

    @Test
    void importsOnlySharedAndSameContextManagementConfigurations() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasBean("managementInfoContributor")
                    .hasBean("samePortInfoContributor").doesNotHaveBean("childPortInfoContributor");
            assertThat(context.getBean(InfoEndpoint.class).info())
                    .containsEntry("management", "shared").containsEntry("scope", "application");
            assertThat(context.getEnvironment().getProperty("local.management.port")).isEqualTo("43123");
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("boundServerPort", Map.of("local.server.port", "43124")));
            assertThat(context.getEnvironment().getProperty("local.management.port")).isEqualTo("43124");
        });
    }

    @Test
    void disablingTheManagementPortDoesNotImportManagementConfigurations() {
        runner.withPropertyValues("management.server.port=-1").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean("managementInfoContributor")
                    .doesNotHaveBean("samePortInfoContributor").doesNotHaveBean("childPortInfoContributor");
            assertThat(context.getBean(InfoEndpoint.class).info()).isEmpty();
            assertThat(context.getEnvironment().getProperty("local.management.port")).isNull();
        });
    }

    @ManagementContextConfiguration(value = ManagementContextType.SAME, proxyBeanMethods = false)
    public static class SamePortConfiguration {

        @Bean
        public InfoContributor samePortInfoContributor() {
            return builder -> builder.withDetail("scope", "application");
        }

    }

    @ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
    public static class ChildPortConfiguration {

        @Bean
        public InfoContributor childPortInfoContributor() {
            return builder -> builder.withDetail("scope", "child");
        }

    }

}
