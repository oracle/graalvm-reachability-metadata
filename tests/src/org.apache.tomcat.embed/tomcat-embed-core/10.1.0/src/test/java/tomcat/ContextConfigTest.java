/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ContextConfigTest {
    @TempDir
    Path directory;

    @Test
    void installsAuthenticatorSelectedByWebDescriptor() throws Exception {
        Path webroot = Files.createDirectories(directory.resolve("secured/WEB-INF"));
        Files.writeString(webroot.resolve("web.xml"), """
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">
                  <login-config><auth-method>BASIC</auth-method><realm-name>catalog</realm-name></login-config>
                </web-app>
                """);
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        Context context = tomcat.addWebapp("/secured", webroot.getParent().toString());
        context.setIgnoreAnnotations(true);
        try {
            tomcat.start();
            assertThat(context.getState().isAvailable()).isTrue();
            assertThat(context.getAuthenticator()).isInstanceOf(BasicAuthenticator.class);
        } finally {
            try {
                tomcat.stop();
            } finally {
                tomcat.destroy();
            }
        }
    }
}
