/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class ConditionalOnManagementPortTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(PortConfiguration.class);

    @Test
    void selectsSharedManagementPortByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasBean("sharedPort").doesNotHaveBean("separatePort")
                    .doesNotHaveBean("disabledPort");
            assertThat(context.getBean("sharedPort", String.class)).isEqualTo("application-server");
        });
    }

    @Test
    void selectsSeparateManagementPort() {
        runner.withPropertyValues("server.port=8080", "management.server.port=9090")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("separatePort").doesNotHaveBean("sharedPort")
                            .doesNotHaveBean("disabledPort");
                    assertThat(context.getBean("separatePort", String.class)).isEqualTo("management-server");
                });
    }

    @Test
    void selectsDisabledManagementPort() {
        runner.withPropertyValues("management.server.port=-1")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("disabledPort").doesNotHaveBean("sharedPort")
                            .doesNotHaveBean("separatePort");
                    assertThat(context.getBean("disabledPort", String.class)).isEqualTo("no-management-server");
                });
    }

    @Test
    void doesNotEnablePortSpecificBeansInANonWebApplication() {
        new ApplicationContextRunner().withUserConfiguration(PortConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("sharedPort")
                        .doesNotHaveBean("separatePort").doesNotHaveBean("disabledPort"));
    }

    @Configuration(proxyBeanMethods = false)
    public static class PortConfiguration {

        @Bean
        @ConditionalOnManagementPort(ManagementPortType.SAME)
        public String sharedPort() {
            return "application-server";
        }

        @Bean
        @ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
        public String separatePort() {
            return "management-server";
        }

        @Bean
        @ConditionalOnManagementPort(ManagementPortType.DISABLED)
        public String disabledPort() {
            return "no-management-server";
        }

    }

}
