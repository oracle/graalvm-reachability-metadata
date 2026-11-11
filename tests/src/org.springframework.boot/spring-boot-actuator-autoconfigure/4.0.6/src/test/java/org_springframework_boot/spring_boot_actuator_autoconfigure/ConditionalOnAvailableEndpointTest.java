/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class ConditionalOnAvailableEndpointTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InfoEndpointAutoConfiguration.class))
            .withBean(InfoContributor.class, () -> builder -> builder.withDetail("service", "catalog"));

    @Test
    void createsAnAccessibleExposedEndpoint() {
        runner.withPropertyValues("management.endpoints.web.exposure.include=info")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(InfoEndpoint.class);
                    assertThat(context.getBean(InfoEndpoint.class).info()).containsEntry("service", "catalog");
                });
    }

    @Test
    void doesNotCreateAnEndpointWithNoAccess() {
        runner.withPropertyValues("management.endpoints.web.exposure.include=info",
                "management.endpoint.info.access=NONE")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InfoEndpoint.class));
    }

    @Test
    void exclusionOverridesExposureIncludes() {
        runner.withPropertyValues("management.endpoints.web.exposure.include=*",
                "management.endpoints.web.exposure.exclude=info")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(InfoEndpoint.class));
    }

}
