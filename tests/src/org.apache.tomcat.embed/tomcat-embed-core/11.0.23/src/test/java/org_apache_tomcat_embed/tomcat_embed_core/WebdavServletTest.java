/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Path;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebdavServletTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void initializesConfiguredPropertyStore() throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(temporaryDirectory.resolve("base").toString());
        tomcat.setPort(0);
        Context context = tomcat.addContext("", temporaryDirectory.toString());
        EmbeddedTomcatSupport.configureContextWithoutWebappScanning(context);
        Wrapper wrapper = Tomcat.addServlet(context, "webdav", new WebdavServlet());
        wrapper.addInitParameter("propertyStore", WebdavServlet.MemoryPropertyStore.class.getName());
        wrapper.setLoadOnStartup(1);
        context.addServletMappingDecoded("/*", "webdav");

        try {
            tomcat.start();

            assertThat(wrapper.getState()).isEqualTo(LifecycleState.STARTED);
            assertThat(wrapper.isUnavailable()).isFalse();
        } finally {
            if (tomcat.getServer().getState().isAvailable()) {
                tomcat.stop();
            }
            if (tomcat.getServer().getState() != LifecycleState.DESTROYED) {
                tomcat.destroy();
            }
        }
    }
}
