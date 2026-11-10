/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_ldap;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.ldap.autoconfigure.LdapAutoConfiguration;
import org.springframework.boot.ldap.autoconfigure.LdapConnectionDetails;
import org.springframework.boot.ldap.autoconfigure.LdapProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class LdapAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LdapAutoConfiguration.class))
            .withPropertyValues(
                    "spring.ldap.base-environment[com.sun.jndi.ldap.connect.timeout]=10000",
                    "spring.ldap.base-environment[com.sun.jndi.ldap.read.timeout]=10000");

    @Test
    void bindsConnectionPropertiesAndQueriesDirectory() throws Exception {
        try (LdapTestServer server = new LdapTestServer()) {
            runner.withPropertyValues(
                    "spring.ldap.urls=" + server.url(),
                    "spring.ldap.base=" + LdapTestServer.BASE,
                    "spring.ldap.username=" + LdapTestServer.USERNAME,
                    "spring.ldap.password=" + LdapTestServer.PASSWORD,
                    "spring.ldap.referral=ignore",
                    "spring.ldap.template.ignore-name-not-found-exception=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        LdapConnectionDetails details = context.getBean(LdapConnectionDetails.class);
                        assertThat(details.getUrls()).containsExactly(server.url());
                        assertThat(details.getBase()).isEqualTo(LdapTestServer.BASE);
                        assertThat(details.getUsername()).isEqualTo(LdapTestServer.USERNAME);
                        assertThat(details.getPassword()).isEqualTo(LdapTestServer.PASSWORD);
                        assertThat(context.getBean(LdapProperties.class).getReferral())
                                .isEqualTo(LdapProperties.Referral.IGNORE);

                        LdapTemplate template = context.getBean(LdapTemplate.class);
                        AttributesMapper<String> surname = attributes -> (String) attributes.get("sn").get();
                        List<String> results = template.search("", "(cn=Ada)", surname);

                        assertThat(results).containsExactly("Lovelace");
                        assertThat(template.search("ou=absent", "(objectClass=person)", surname)).isEmpty();
                    });
        }
    }

    @Test
    void suppliedConnectionDetailsOverrideConnectionProperties() throws Exception {
        try (LdapTestServer server = new LdapTestServer()) {
            LdapConnectionDetails connection = new LdapConnectionDetails() {
                @Override
                public String[] getUrls() {
                    return new String[]{server.url()};
                }

                @Override
                public String getBase() {
                    return LdapTestServer.BASE;
                }

                @Override
                public String getUsername() {
                    return LdapTestServer.USERNAME;
                }

                @Override
                public String getPassword() {
                    return LdapTestServer.PASSWORD;
                }
            };
            runner.withBean(LdapConnectionDetails.class, () -> connection)
                    .withPropertyValues("spring.ldap.password=unused-property-password")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(LdapConnectionDetails.class)).isSameAs(connection);
                        LdapTemplate template = context.getBean(LdapTemplate.class);
                        String surname = template.lookup("cn=Ada",
                                (AttributesMapper<String>) attributes -> (String) attributes.get("sn").get());

                        assertThat(surname).isEqualTo("Lovelace");
                    });
        }
    }
}
