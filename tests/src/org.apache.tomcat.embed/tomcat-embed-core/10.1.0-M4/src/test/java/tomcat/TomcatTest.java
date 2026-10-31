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
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatTest {
    @TempDir
    Path directory;

    @Test
    void createsConfiguredContextAndWebappListener() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("server").toString());
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setContextClass(CatalogContext.class.getName());
        host.setConfigClass(ContextConfig.class.getName());
        Path webapp = Files.createDirectory(directory.resolve("webapp"));
        try {
            Context context = tomcat.addContext(host, "/catalog", webapp.toString());
            assertThat(context).isInstanceOf(CatalogContext.class);
            assertThat(host.findChild("/catalog")).isSameAs(context);
            Context deployed = tomcat.addWebapp(host, "/shop", webapp.toString());
            assertThat(deployed).isInstanceOf(CatalogContext.class);
            assertThat(deployed.getPath()).isEqualTo("/shop");
            assertThat(deployed.findLifecycleListeners()).anyMatch(ContextConfig.class::isInstance);
        } finally {
            tomcat.destroy();
        }
    }

    public static class CatalogContext extends StandardContext {
    }
}
