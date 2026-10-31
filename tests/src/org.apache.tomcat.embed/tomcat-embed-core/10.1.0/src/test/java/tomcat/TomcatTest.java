/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.util.Arrays;

import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TomcatTest {
    @TempDir
    File base;

    @Test
    void addsWebappUsingConfiguredContextAndConfigurationClasses() throws Exception {
        Tomcat server = new Tomcat();
        server.setBaseDir(base.getAbsolutePath());
        try {
            StandardHost host = (StandardHost) server.getHost();
            host.setContextClass(ApplicationContext.class.getName());
            host.setConfigClass(ApplicationConfiguration.class.getName());
            Context context = server.addWebapp("/catalog", base.getAbsolutePath());
            assertInstanceOf(ApplicationContext.class, context);
            assertEquals("/catalog", context.getPath());
            assertEquals(base.getAbsolutePath(), context.getDocBase());
            assertTrue(Arrays.stream(context.findLifecycleListeners())
                    .anyMatch(ApplicationConfiguration.class::isInstance));
            assertEquals(context, host.findChild("/catalog"));
        } finally {
            server.destroy();
        }
    }

    public static class ApplicationContext extends StandardContext {
    }

    public static class ApplicationConfiguration extends ContextConfig {
    }
}
