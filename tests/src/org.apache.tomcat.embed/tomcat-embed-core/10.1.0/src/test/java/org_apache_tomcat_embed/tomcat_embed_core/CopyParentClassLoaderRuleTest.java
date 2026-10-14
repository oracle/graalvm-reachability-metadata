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
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.startup.Catalina;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyParentClassLoaderRuleTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void copiesEngineParentClassLoaderToParsedHost() throws Exception {
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
        Catalina catalina = new Catalina();
        catalina.setConfigFile(serverXml.toUri().toString());
        catalina.load();

        Server server = catalina.getServer();
        try {
            Service service = server.findServices()[0];
            Container engine = service.getContainer();
            Container host = engine.findChild("localhost");

            assertThat(host.getParentClassLoader()).isSameAs(engine.getParentClassLoader());
        } finally {
            server.destroy();
        }
    }
}
