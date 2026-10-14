/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Path;
import java.util.Arrays;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.FailedContext;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatTest {

    @Test
    void createsConfiguredWebApplicationContext(@TempDir Path directory) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        tomcat.setAddDefaultWebXmlToWebapp(false);

        try {
            Context context = tomcat.addWebapp(null, "/application", directory.toAbsolutePath().toString());
            LifecycleListener[] listeners = context.findLifecycleListeners();

            assertThat(context.getPath()).isEqualTo("/application");
            assertThat(context.getDocBase()).isEqualTo(directory.toAbsolutePath().toString());
            assertThat(Arrays.stream(listeners)).anyMatch(ContextConfig.class::isInstance);
        } finally {
            tomcat.destroy();
        }
    }

    @Test
    void createsHostConfiguredContextImplementation(@TempDir Path directory) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        ((StandardHost) tomcat.getHost()).setContextClass(FailedContext.class.getName());

        try {
            Context context = tomcat.addContext(null, "/custom", directory.toAbsolutePath().toString());

            assertThat(context).isInstanceOf(FailedContext.class);
            assertThat(context.getPath()).isEqualTo("/custom");
        } finally {
            tomcat.destroy();
        }
    }
}
