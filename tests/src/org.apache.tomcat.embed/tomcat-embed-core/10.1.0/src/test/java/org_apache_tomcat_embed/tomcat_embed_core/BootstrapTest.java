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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Order(2)
public class BootstrapTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void initializesLoadsStartsAndStopsCatalinaDaemon() throws Exception {
        Path serverXml = temporaryDirectory.resolve("server.xml");
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

        System.setProperty("catalina.base", Path.of("").toAbsolutePath().toString());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.init(new String[] {"-config", serverXml.toString(), "-nonaming", "start"});
        bootstrap.setAwait(false);

        try {
            bootstrap.start();
            assertThat(Bootstrap.getCatalinaBaseFile()).isDirectory();
        } finally {
            bootstrap.stop();
        }
    }
}
