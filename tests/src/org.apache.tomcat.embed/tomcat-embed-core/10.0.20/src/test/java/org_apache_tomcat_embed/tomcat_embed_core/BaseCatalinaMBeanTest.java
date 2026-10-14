/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Path;

import org.apache.catalina.core.StandardHost;
import org.apache.catalina.mbeans.ContainerMBean;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.RemoteAddrValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseCatalinaMBeanTest {

    @Test
    void addsConfiguredValveToManagedContainer(@TempDir Path directory) throws Exception {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        StandardHost host = (StandardHost) tomcat.getHost();

        try {
            tomcat.start();
            ContainerMBean mBean = new ContainerMBean();
            mBean.setManagedResource(host, "ObjectReference");

            String objectName = mBean.addValve(RemoteAddrValve.class.getName());

            assertThat(host.getPipeline().getValves()).hasAtLeastOneElementOfType(RemoteAddrValve.class);
            assertThat(objectName).contains("type=Valve");
        } finally {
            tomcat.stop();
            tomcat.destroy();
        }
    }
}
