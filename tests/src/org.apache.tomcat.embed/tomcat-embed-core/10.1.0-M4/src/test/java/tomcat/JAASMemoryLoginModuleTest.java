package tomcat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import org.apache.catalina.realm.JAASMemoryLoginModule;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JAASMemoryLoginModuleTest {
    @TempDir
    Path directory;

    @Test
    void authenticatesConfiguredUsersWithNamedCredentialHandler() throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("secret".getBytes(StandardCharsets.UTF_8)));
        Path users = directory.resolve("users.xml");
        Files.writeString(users, """
                <tomcat-users>
                  <user username="reader" password="%s" roles="catalog"/>
                </tomcat-users>
                """.formatted(digest));
        Subject subject = new Subject();
        JAASMemoryLoginModule module = new JAASMemoryLoginModule();
        module.initialize(subject, callbacks -> {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback name) {
                    name.setName("reader");
                } else if (callback instanceof PasswordCallback password) {
                    password.setPassword("secret".toCharArray());
                }
            }
        }, Map.of(), Map.of("pathname", users.toString(),
                "credentialHandlerClassName", MessageDigestCredentialHandler.class.getName(),
                "algorithm", "SHA-256"));
        assertThat(module.login()).isTrue();
        assertThat(module.commit()).isTrue();
        assertThat(subject.getPrincipals()).anyMatch(principal -> principal.getName().equals("reader"));
        assertThat(subject.getPrincipals()).anyMatch(principal -> principal.getName().equals("catalog"));
        assertThat(module.logout()).isTrue();
        assertThat(subject.getPrincipals()).noneMatch(principal -> principal.getName().equals("reader"));
    }
}
