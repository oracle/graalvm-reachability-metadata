/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_ldap;

import java.util.Map;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.ldap.autoconfigure.LdapAutoConfiguration;
import org.springframework.boot.ldap.autoconfigure.embedded.EmbeddedLdapAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class EmbeddedLdapAutoConfigurationTest {

    @Test
    void startsDirectoryImportsLdifAndPublishesItsPort() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EmbeddedLdapAutoConfiguration.class, LdapAutoConfiguration.class))
                .withPropertyValues(
                        "spring.ldap.base=dc=example,dc=org",
                        "spring.ldap.embedded.base-dn=dc=example,dc=org",
                        "spring.ldap.embedded.port=0",
                        "spring.ldap.embedded.credential.username=cn=manager",
                        "spring.ldap.embedded.credential.password=directory-secret",
                        "spring.ldap.embedded.ldif=classpath:directory.ldif")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    InMemoryDirectoryServer server = context.getBean(InMemoryDirectoryServer.class);
                    int port = server.getListenPort();
                    assertThat(port).isPositive();
                    assertThat(context.getEnvironment().getProperty("local.ldap.port", Integer.class))
                            .isEqualTo(port);

                    LdapContextSource source = context.getBean(LdapContextSource.class);
                    assertThat(source.getUrls()).containsExactly("ldap://localhost:" + port);
                    source.setBaseEnvironmentProperties(Map.of(
                            "com.sun.jndi.ldap.connect.timeout", "10000",
                            "com.sun.jndi.ldap.read.timeout", "10000"));
                    source.afterPropertiesSet();
                    LdapTemplate template = context.getBean(LdapTemplate.class);

                    String surname = template.lookup("cn=Grace",
                            (AttributesMapper<String>) attributes -> (String) attributes.get("sn").get());

                    assertThat(surname).isEqualTo("Hopper");
                });
    }
}
