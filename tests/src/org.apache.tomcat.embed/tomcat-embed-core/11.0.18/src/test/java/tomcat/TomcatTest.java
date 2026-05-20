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
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatTest {

    @Test
    void addWebappCreatesDefaultContextConfigReflectively() throws Exception {
        Tomcat tomcat = new Tomcat();
        Path docBase = Files.createTempDirectory("tomcat-webapp");
        try {
            Context context = tomcat.addWebapp("/sample", docBase.toString());

            assertThat(context.getPath()).isEqualTo("/sample");
            assertThat(context.findLifecycleListeners()).hasAtLeastOneElementOfType(
                    org.apache.catalina.startup.ContextConfig.class);
        } finally {
            tomcat.destroy();
        }
    }
}
