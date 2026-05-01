/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.UserToRolesMapper;
import org.h2.security.auth.DefaultAuthenticator;
import org.h2.security.auth.impl.StaticRolesMapper;
import org.h2.security.auth.impl.StaticUserCredentialsValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthenticatorTest {
    @TempDir
    Path tempDir;

    @Test
    void configuresRealmValidatorAndRoleMapperFromXml() throws Exception {
        Path configFile = tempDir.resolve("h2auth.xml");
        Files.writeString(configFile, """
                <h2Auth allowUserRegistration="true" createMissingRoles="false">
                    <realm name="testRealm" validatorClass="%s">
                        <property name="userNamePattern" value=".*"/>
                        <property name="password" value="secret"/>
                    </realm>
                    <userToRolesMapper className="%s">
                        <property name="roles" value="admin,reader"/>
                    </userToRolesMapper>
                </h2Auth>
                """.formatted(StaticUserCredentialsValidator.class.getName(), StaticRolesMapper.class.getName()),
                StandardCharsets.UTF_8);

        DefaultAuthenticator authenticator = new DefaultAuthenticator(true);
        authenticator.configureFromUrl(configFile.toUri().toURL());

        assertThat(authenticator.isAllowUserRegistration()).isTrue();
        assertThat(authenticator.isCreateMissingRoles()).isFalse();
        assertThat(authenticator.getUserToRolesMappers()).hasSize(1);

        UserToRolesMapper mapper = authenticator.getUserToRolesMappers().get(0);
        assertThat(mapper).isInstanceOf(StaticRolesMapper.class);
        assertThat(mapper.mapUserToRoles(null)).containsExactlyInAnyOrder("admin", "reader");
    }
}
