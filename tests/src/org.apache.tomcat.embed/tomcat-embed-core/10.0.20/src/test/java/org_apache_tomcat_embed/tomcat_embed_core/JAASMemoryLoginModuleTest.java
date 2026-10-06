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
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.JAASMemoryLoginModule;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JAASMemoryLoginModuleTest {

    @Test
    void authenticatesUserWithConfiguredCredentialHandler(@TempDir Path directory) throws Exception {
        Path users = directory.resolve("tomcat-users.xml");
        Files.writeString(users, """
                <tomcat-users>
                  <user username="alice" password="secret" roles="admin"/>
                </tomcat-users>
                """);
        JAASMemoryLoginModule module = new JAASMemoryLoginModule();
        Subject subject = new Subject();

        module.initialize(subject, callbacks -> {
            for (var callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName("alice");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword("secret".toCharArray());
                }
            }
        }, Map.of(), Map.of("pathname", users.toString(), "credentialHandlerClassName",
                MessageDigestCredentialHandler.class.getName()));

        assertThat(module.login()).isTrue();
        assertThat(module.commit()).isTrue();
        assertThat(subject.getPrincipals(GenericPrincipal.class)).extracting(GenericPrincipal::getName)
                .contains("alice", "admin");
    }
}
