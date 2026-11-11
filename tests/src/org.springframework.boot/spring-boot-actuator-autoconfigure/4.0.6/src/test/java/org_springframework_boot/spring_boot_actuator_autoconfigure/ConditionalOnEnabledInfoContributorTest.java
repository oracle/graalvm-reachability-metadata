/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_actuator_autoconfigure;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.info.InfoContributorAutoConfiguration;
import org.springframework.boot.actuate.info.BuildInfoContributor;
import org.springframework.boot.actuate.info.EnvironmentInfoContributor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class ConditionalOnEnabledInfoContributorTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InfoContributorAutoConfiguration.class));

    @Test
    void explicitlyEnablesEnvironmentDetails() {
        runner.withPropertyValues("management.info.env.enabled=true", "info.service=catalog")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EnvironmentInfoContributor.class);
                    Info.Builder builder = new Info.Builder();
                    context.getBean(EnvironmentInfoContributor.class).contribute(builder);
                    assertThat(builder.build().getDetails()).containsEntry("service", "catalog");
                });
    }

    @Test
    void environmentDetailsAreOptIn() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EnvironmentInfoContributor.class));
    }

    @Test
    void explicitlyDisablesEnvironmentDetails() {
        runner.withPropertyValues("management.info.env.enabled=false", "info.service=catalog")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(EnvironmentInfoContributor.class));
    }

    @Test
    void buildContributorUsesTheDefaultsProperty() {
        Properties properties = new Properties();
        properties.setProperty("name", "catalog-service");
        ApplicationContextRunner buildRunner = runner.withBean(BuildProperties.class,
                () -> new BuildProperties(properties));
        buildRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(BuildInfoContributor.class);
            Info.Builder builder = new Info.Builder();
            context.getBean(BuildInfoContributor.class).contribute(builder);
            assertThat(builder.build().getDetails().get("build")).isEqualTo(properties);
        });
        buildRunner.withPropertyValues("management.info.defaults.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(BuildInfoContributor.class));
    }

}
