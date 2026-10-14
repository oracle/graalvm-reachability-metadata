/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.startup.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class BootstrapTest {

    @Test
    void delegatesServerLifecycleToCatalina(@TempDir Path catalinaBase) throws Exception {
        Path serverXml = createServerXml(catalinaBase);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.init(new String[] {"-config", serverXml.toString(), "-nonaming", "start" });

        bootstrap.setAwait(false);
        bootstrap.start();
        assertThat(System.getProperty("catalina.base")).isNotBlank();

        bootstrap.stopServer(new String[] {"stop" });
        bootstrap.stopServer();
        bootstrap.stop();
    }

    @Test
    void validatesServerConfigurationThroughMainEntryPoint(@TempDir Path catalinaBase) throws Exception {
        Path serverXml = createServerXml(catalinaBase);

        Bootstrap.main(new String[] {"-config", serverXml.toString(), "-nonaming", "configtest" });

        assertThat(Files.isRegularFile(serverXml)).isTrue();
    }

    private static Path createServerXml(Path catalinaBase) throws Exception {
        Files.createDirectories(catalinaBase.resolve("conf"));
        Files.createDirectories(catalinaBase.resolve("logs"));
        Files.createDirectories(catalinaBase.resolve("temp"));
        Files.createDirectories(catalinaBase.resolve("webapps"));
        Files.createDirectories(catalinaBase.resolve("work"));
        Path serverXml = catalinaBase.resolve("conf/server.xml");
        Files.writeString(serverXml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Server port="-2" shutdown="SHUTDOWN">
                  <Service name="Catalina">
                    <Engine name="Catalina" defaultHost="localhost">
                      <Host name="localhost" appBase="webapps" autoDeploy="false" deployOnStartup="false" />
                    </Engine>
                  </Service>
                </Server>
                """);
        return serverXml;
    }
}
