/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import org.apache.catalina.CredentialHandler;
import org.apache.catalina.realm.JAASMemoryLoginModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JAASMemoryLoginModuleTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void authenticatesUsersWithConfiguredCredentialHandler() throws Exception {
        Path users = temporaryDirectory.resolve("tomcat-users.xml");
        Files.writeString(users, """
                <tomcat-users>
                  <user username="alice" password="secret" roles="admin"/>
                </tomcat-users>
                """);
        Subject subject = new Subject();
        JAASMemoryLoginModule module = new JAASMemoryLoginModule();
        CallbackHandler callbacks = JAASMemoryLoginModuleTest::supplyCredentials;
        Map<String, String> options = Map.of("pathname", users.toString(), "credentialHandlerClassName",
                PlainTextCredentialHandler.class.getName());

        module.initialize(subject, callbacks, Map.of(), options);

        assertThat(module.login()).isTrue();
        assertThat(module.commit()).isTrue();
        assertThat(subject.getPrincipals().stream().map(principal -> principal.getName())).contains("alice");
        assertThat(module.logout()).isTrue();
        assertThat(subject.getPrincipals().stream().map(principal -> principal.getName()))
                .contains("admin")
                .doesNotContain("alice");
    }

    private static void supplyCredentials(Callback[] callbacks) {
        ((NameCallback) callbacks[0]).setName("alice");
        ((PasswordCallback) callbacks[1]).setPassword("secret".toCharArray());
    }

    public static class PlainTextCredentialHandler implements CredentialHandler {
        @Override
        public boolean matches(String inputCredentials, String storedCredentials) {
            return inputCredentials.equals(storedCredentials);
        }

        @Override
        public String mutate(String inputCredentials) {
            return inputCredentials;
        }
    }
}
