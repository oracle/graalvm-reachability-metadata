/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.startup.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Order(2)
public class BootstrapTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    private Path temporaryDirectory;

    @AfterEach
    void restoreStandardStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void delegatesSupportedLifecycleCommandsToCatalina() throws Exception {
        Path serverXml = createServerXml();
        String[] startArguments = {"-config", serverXml.toString(), "-nonaming", "start"};

        Bootstrap.main(startArguments);
        assertThat(System.getProperty("catalina.useNaming")).isEqualTo("false");

        Bootstrap lifecycle = new Bootstrap();
        lifecycle.init(startArguments);
        lifecycle.setAwait(false);
        try {
            lifecycle.start();
            assertThat(Bootstrap.getCatalinaBaseFile()).isDirectory();
            assertThat(Bootstrap.getCatalinaHomeFile()).isDirectory();
        } finally {
            lifecycle.stop();
        }

        Bootstrap stopWithArguments = new Bootstrap();
        stopWithArguments.init(startArguments);
        stopWithArguments.stopServer(new String[] {"stop"});

        Bootstrap stopWithoutArguments = new Bootstrap();
        stopWithoutArguments.init(startArguments);
        stopWithoutArguments.stopServer();
    }

    private Path createServerXml() throws IOException {
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
        return serverXml;
    }
}
