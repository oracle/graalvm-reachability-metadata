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
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatTest {
    @TempDir
    Path directory;

    @Test
    void usesHostSelectedContextAndConfigurationListener() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.toString());
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setContextClass(ApplicationContext.class.getName());
        Path webapp = Files.createDirectories(directory.resolve("app"));
        Context context = tomcat.addWebapp(host, "/catalog", webapp.toString());
        try {
            assertThat(context).isInstanceOf(ApplicationContext.class);
            assertThat(context.getPath()).isEqualTo("/catalog");
            assertThat(context.findLifecycleListeners()).anyMatch(listener ->
                    listener.getClass().getName().equals(host.getConfigClass()));
        } finally {
            tomcat.destroy();
        }
    }

    public static class ApplicationContext extends StandardContext {
    }
}
