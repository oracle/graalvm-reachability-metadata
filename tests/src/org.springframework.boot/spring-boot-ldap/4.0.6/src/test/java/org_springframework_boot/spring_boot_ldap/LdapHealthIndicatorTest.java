/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_ldap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.ldap.autoconfigure.health.LdapHealthContributorAutoConfiguration;
import org.springframework.boot.ldap.health.LdapHealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ldap.core.LdapTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class LdapHealthIndicatorTest {

    @Test
    void autoConfiguredHealthIndicatorConnectsAndReportsLdapVersion() throws Exception {
        try (LdapTestServer server = new LdapTestServer()) {
            LdapTemplate template = server.template(LdapTestServer.PASSWORD);
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(LdapHealthContributorAutoConfiguration.class))
                    .withBean("ldapTemplate", LdapTemplate.class, () -> template)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        LdapHealthIndicator indicator = context.getBean(
                                "ldapHealthContributor", LdapHealthIndicator.class);

                        Health health = indicator.health();

                        assertThat(health.getStatus()).isEqualTo(Status.UP);
                        assertThat(health.getDetails()).containsEntry("version", "3");
                    });
        }
    }

    @Test
    void rejectedCredentialsAreReportedAsUnhealthy() throws Exception {
        try (LdapTestServer server = new LdapTestServer()) {
            LdapHealthIndicator indicator = new LdapHealthIndicator(server.template("incorrect-password"));

            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsKey("error");
            assertThat(health.getDetails()).doesNotContainKey("version");
        }
    }
}
