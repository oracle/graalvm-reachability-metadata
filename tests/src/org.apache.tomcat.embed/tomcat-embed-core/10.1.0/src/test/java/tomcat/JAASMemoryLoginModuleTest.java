/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import org.apache.catalina.realm.JAASMemoryLoginModule;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JAASMemoryLoginModuleTest {
    @TempDir
    File directory;

    @Test
    void authenticatesUserFromConfiguredRealmFile() throws Exception {
        File users = new File(directory, "users.xml");
        Files.writeString(users.toPath(), """
                <tomcat-users><user username="alice" password="secret" roles="reader"/></tomcat-users>
                """);
        Subject subject = new Subject();
        JAASMemoryLoginModule module = new JAASMemoryLoginModule();
        module.initialize(subject, callbacks -> {
            for (var callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName("alice");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword("secret".toCharArray());
                }
            }
        }, Map.of(), Map.of("pathname", users.getAbsolutePath(),
                "credentialHandlerClassName", MessageDigestCredentialHandler.class.getName()));
        assertTrue(module.login());
        assertTrue(module.commit());
        assertTrue(subject.getPrincipals().stream().anyMatch(principal -> principal.getName().equals("alice")));
        assertTrue(subject.getPrincipals().stream().anyMatch(principal -> principal.getName().equals("reader")));
        assertTrue(module.logout());
        assertTrue(subject.getPrincipals().stream().noneMatch(principal -> principal.getName().equals("alice")));
    }
}
