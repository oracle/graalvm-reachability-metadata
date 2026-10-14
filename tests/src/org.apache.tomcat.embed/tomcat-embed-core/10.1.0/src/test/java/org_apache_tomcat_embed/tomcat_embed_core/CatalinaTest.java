/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.startup.Catalina;
import org.apache.catalina.startup.EngineConfig;
import org.apache.catalina.startup.HostConfig;
import org.apache.tomcat.util.digester.Digester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class CatalinaTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsGeneratedCodeProviderAndParsesServerXmlRules() throws Exception {
        Path serverXml = temporaryDirectory.resolve("server.xml");
        Files.writeString(serverXml, """
                <Server port="-2" shutdown="SHUTDOWN">
                  <Service name="rules">
                    <Engine name="engine" defaultHost="localhost">
                      <Host name="localhost" appBase="webapps" />
                    </Engine>
                  </Service>
                </Server>
                """);
        Catalina generatedCatalina = new Catalina();
        generatedCatalina.setConfigFile(serverXml.toUri().toString());
        generatedCatalina.setUseGeneratedCode(true);
        generatedCatalina.setGeneratedCodePackage(getClass().getPackageName());

        try {
            generatedCatalina.load();

            assertThat(generatedCatalina.getServer()).isNotNull();
            assertThat(DigesterGeneratedCodeLoader.getRequestedClassName())
                    .isEqualTo(getClass().getPackageName() + ".ServerXml");
        } finally {
            destroy(generatedCatalina.getServer());
            Digester.setGeneratedCodeLoader(null);
        }

        Catalina parsedCatalina = new Catalina();
        parsedCatalina.setConfigFile(serverXml.toString());
        try {
            parsedCatalina.load();

            Service service = parsedCatalina.getServer().findServices()[0];
            Container engine = service.getContainer();
            Container host = engine.findChildren()[0];
            assertThat(host.getParentClassLoader()).isSameAs(engine.getParentClassLoader());
            assertThat(engine.findLifecycleListeners()).anyMatch(EngineConfig.class::isInstance);
            assertThat(host.findLifecycleListeners()).anyMatch(HostConfig.class::isInstance);
        } finally {
            destroy(parsedCatalina.getServer());
        }
    }

    private static void destroy(Server server) throws Exception {
        if (server != null && server.getState() != LifecycleState.DESTROYED) {
            server.destroy();
        }
    }
}
