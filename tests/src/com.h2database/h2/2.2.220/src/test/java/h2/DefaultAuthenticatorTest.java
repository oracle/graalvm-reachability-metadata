/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.security.auth.DefaultAuthenticator;
import org.h2.security.auth.impl.StaticRolesMapper;
import org.h2.security.auth.impl.StaticUserCredentialsValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthenticatorTest {
    @Test
    void configuresRealmsAndRoleMappersFromXml() throws Exception {
        Path configFile = Files.createTempFile("h2-authenticator", ".xml");
        try {
            Files.writeString(configFile, """
                    <h2Auth allowUserRegistration="false" createMissingRoles="true">
                        <realm name="integration" validatorClass="%s">
                            <property name="userNamePattern" value="INTEGRATION_USER"/>
                            <property name="password" value="s3cr3t"/>
                        </realm>
                        <userToRolesMapper className="%s">
                            <property name="roles" value="ADMIN,REPORTING"/>
                        </userToRolesMapper>
                    </h2Auth>
                    """.formatted(
                    StaticUserCredentialsValidator.class.getName(),
                    StaticRolesMapper.class.getName()), StandardCharsets.UTF_8);

            DefaultAuthenticator authenticator = new DefaultAuthenticator(true);
            authenticator.configureFromUrl(configFile.toUri().toURL());

            assertThat(authenticator.isAllowUserRegistration()).isFalse();
            assertThat(authenticator.isCreateMissingRoles()).isTrue();
            assertThat(authenticator.getUserToRolesMappers()).hasSize(1);

            Collection<String> roles = authenticator.getUserToRolesMappers().get(0).mapUserToRoles(null);
            assertThat(roles).containsExactlyInAnyOrder("ADMIN", "REPORTING");
        } finally {
            Files.deleteIfExists(configFile);
        }
    }
}
