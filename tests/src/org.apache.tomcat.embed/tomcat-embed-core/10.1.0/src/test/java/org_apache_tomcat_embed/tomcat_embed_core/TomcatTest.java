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
    void addsWebappWithReflectivelyConfiguredLifecycleListener(@TempDir Path webappDirectory) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(webappDirectory.resolve("base").toString());
        tomcat.setAddDefaultWebXmlToWebapp(false);

        try {
            Context context = tomcat.addWebapp(null, "/webapp", webappDirectory.toAbsolutePath().toString());
            LifecycleListener[] listeners = context.findLifecycleListeners();

            assertThat(context.getPath()).isEqualTo("/webapp");
            assertThat(context.getDocBase()).isEqualTo(webappDirectory.toAbsolutePath().toString());
            assertThat(Arrays.stream(listeners)).anyMatch(ContextConfig.class::isInstance);
        } finally {
            tomcat.destroy();
        }
    }

    @Test
    void addsConfiguredContextImplementation(@TempDir Path docBase) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(docBase.resolve("base").toString());
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setContextClass(FailedContext.class.getName());

        try {
            Context context = tomcat.addContext(null, "/custom", docBase.toAbsolutePath().toString());

            assertThat(context).isInstanceOf(FailedContext.class);
            assertThat(context.getPath()).isEqualTo("/custom");
            assertThat(context.getDocBase()).isEqualTo(docBase.toAbsolutePath().toString());
        } finally {
            tomcat.destroy();
        }
    }
}
