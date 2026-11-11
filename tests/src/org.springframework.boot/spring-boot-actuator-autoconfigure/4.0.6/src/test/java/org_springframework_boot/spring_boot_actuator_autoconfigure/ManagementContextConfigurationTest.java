/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagementContextConfigurationTest {

    @Test
    void contributesManagementInformationThroughConfigurationBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(InfoEndpointAutoConfiguration.class))
                .withUserConfiguration(SharedManagementConfiguration.class)
                .withPropertyValues("management.endpoints.web.exposure.include=info")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(InfoEndpoint.class);
                    assertThat(context.getBean(InfoEndpoint.class).info()).containsEntry("management", "shared");
                });
    }

    @ManagementContextConfiguration(proxyBeanMethods = false)
    public static class SharedManagementConfiguration {

        @Bean
        public InfoContributor managementInfoContributor() {
            return builder -> builder.withDetail("management", "shared");
        }

    }

}
