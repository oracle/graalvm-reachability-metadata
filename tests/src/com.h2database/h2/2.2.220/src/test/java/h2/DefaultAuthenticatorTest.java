/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.api.CredentialsValidator;
import org.h2.api.UserToRolesMapper;
import org.h2.security.auth.AuthenticationException;
import org.h2.security.auth.AuthenticationInfo;
import org.h2.security.auth.ConfigProperties;
import org.h2.security.auth.DefaultAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthenticatorTest {
    @TempDir
    Path tempDirectory;

    @BeforeEach
    void resetCounters() {
        RecordingCredentialsValidator.constructorCount.set(0);
        RecordingCredentialsValidator.configureCount.set(0);
        RecordingCredentialsValidator.expectedPassword = null;
        RecordingUserToRolesMapper.constructorCount.set(0);
        RecordingUserToRolesMapper.configureCount.set(0);
        RecordingUserToRolesMapper.roles = List.of();
    }

    @Test
    void configuresRealmsAndRoleMappersFromXml() throws Exception {
        Path configFile = writeAuthConfig();
        DefaultAuthenticator authenticator = new DefaultAuthenticator(true);

        authenticator.configureFromUrl(configFile.toUri().toURL());

        assertThat(authenticator.isAllowUserRegistration()).isTrue();
        assertThat(authenticator.isCreateMissingRoles()).isFalse();
        assertThat(RecordingCredentialsValidator.constructorCount).hasValue(1);
        assertThat(RecordingCredentialsValidator.configureCount).hasValue(1);
        assertThat(RecordingCredentialsValidator.expectedPassword).isEqualTo("secret");
        assertThat(RecordingUserToRolesMapper.constructorCount).hasValue(1);
        assertThat(RecordingUserToRolesMapper.configureCount).hasValue(1);
        assertThat(authenticator.getUserToRolesMappers()).hasSize(1);
        assertThat(authenticator.getUserToRolesMappers().get(0).mapUserToRoles(null))
                .containsExactly("APP_USER", "REPORTING");
    }

    private Path writeAuthConfig() throws IOException {
        Path configFile = tempDirectory.resolve("h2-auth.xml");
        String config = """
                <h2Auth allowUserRegistration="true" createMissingRoles="false">
                    <realm name="custom" validatorClass="%s">
                        <property name="expectedPassword" value="secret"/>
                    </realm>
                    <userToRolesMapper className="%s">
                        <property name="roles" value="APP_USER,REPORTING"/>
                    </userToRolesMapper>
                </h2Auth>
                """.formatted(
                RecordingCredentialsValidator.class.getName(),
                RecordingUserToRolesMapper.class.getName());
        Files.writeString(configFile, config);
        return configFile;
    }

    public static class RecordingCredentialsValidator implements CredentialsValidator {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger configureCount = new AtomicInteger();
        static volatile String expectedPassword;

        public RecordingCredentialsValidator() {
            constructorCount.incrementAndGet();
        }

        @Override
        public void configure(ConfigProperties configProperties) {
            configureCount.incrementAndGet();
            expectedPassword = configProperties.getStringValue("expectedPassword");
        }

        @Override
        public boolean validateCredentials(AuthenticationInfo authenticationInfo) {
            return expectedPassword.equals(authenticationInfo.getPassword());
        }
    }

    public static class RecordingUserToRolesMapper implements UserToRolesMapper {
        static final AtomicInteger constructorCount = new AtomicInteger();
        static final AtomicInteger configureCount = new AtomicInteger();
        static volatile List<String> roles = List.of();

        public RecordingUserToRolesMapper() {
            constructorCount.incrementAndGet();
        }

        @Override
        public void configure(ConfigProperties configProperties) {
            configureCount.incrementAndGet();
            roles = Arrays.asList(configProperties.getStringValue("roles").split(","));
        }

        @Override
        public Collection<String> mapUserToRoles(AuthenticationInfo authenticationInfo) throws AuthenticationException {
            return roles;
        }
    }
}
