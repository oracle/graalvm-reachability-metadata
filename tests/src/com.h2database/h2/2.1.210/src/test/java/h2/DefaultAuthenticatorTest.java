/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.CredentialsValidator;
import org.h2.api.UserToRolesMapper;
import org.h2.security.auth.AuthenticationException;
import org.h2.security.auth.AuthenticationInfo;
import org.h2.security.auth.ConfigProperties;
import org.h2.security.auth.DefaultAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAuthenticatorTest {
    @TempDir
    private Path temporaryDirectory;

    @BeforeEach
    void resetConfiguredComponents() {
        RecordingCredentialsValidator.constructorCalls.set(0);
        RecordingCredentialsValidator.configuredRealm.set(null);
        RecordingUserToRolesMapper.constructorCalls.set(0);
        RecordingUserToRolesMapper.configuredRole.set(null);
    }

    @Test
    void configuresCredentialValidatorAndUserToRolesMapperFromXml() throws Exception {
        Path configFile = temporaryDirectory.resolve("h2auth.xml");
        Files.writeString(configFile, """
                <h2Auth allowUserRegistration="true" createMissingRoles="false">
                    <realm name="testRealm" validatorClass="%s">
                        <property name="realm" value="testRealm"/>
                    </realm>
                    <userToRolesMapper className="%s">
                        <property name="role" value="AUTHENTICATED_USER"/>
                    </userToRolesMapper>
                </h2Auth>
                """.formatted(
                RecordingCredentialsValidator.class.getName(),
                RecordingUserToRolesMapper.class.getName()));

        DefaultAuthenticator authenticator = new DefaultAuthenticator(true);
        authenticator.configureFromUrl(configFile.toUri().toURL());

        assertThat(authenticator.isAllowUserRegistration()).isTrue();
        assertThat(authenticator.isCreateMissingRoles()).isFalse();
        assertThat(RecordingCredentialsValidator.constructorCalls.get()).isEqualTo(1);
        assertThat(RecordingCredentialsValidator.configuredRealm.get()).isEqualTo("testRealm");
        assertThat(RecordingUserToRolesMapper.constructorCalls.get()).isEqualTo(1);
        assertThat(RecordingUserToRolesMapper.configuredRole.get()).isEqualTo("AUTHENTICATED_USER");
        assertThat(authenticator.getUserToRolesMappers())
                .hasSize(1)
                .allMatch(RecordingUserToRolesMapper.class::isInstance);
    }

    public static final class RecordingCredentialsValidator implements CredentialsValidator {
        private static final AtomicInteger constructorCalls = new AtomicInteger();
        private static final AtomicReference<String> configuredRealm = new AtomicReference<>();

        public RecordingCredentialsValidator() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void configure(ConfigProperties configProperties) {
            configuredRealm.set(configProperties.getStringValue("realm"));
        }

        @Override
        public boolean validateCredentials(AuthenticationInfo authenticationInfo) {
            return true;
        }
    }

    public static final class RecordingUserToRolesMapper implements UserToRolesMapper {
        private static final AtomicInteger constructorCalls = new AtomicInteger();
        private static final AtomicReference<String> configuredRole = new AtomicReference<>();

        public RecordingUserToRolesMapper() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public void configure(ConfigProperties configProperties) {
            configuredRole.set(configProperties.getStringValue("role"));
        }

        @Override
        public Collection<String> mapUserToRoles(AuthenticationInfo authenticationInfo) throws AuthenticationException {
            return List.of(configuredRole.get());
        }
    }
}
