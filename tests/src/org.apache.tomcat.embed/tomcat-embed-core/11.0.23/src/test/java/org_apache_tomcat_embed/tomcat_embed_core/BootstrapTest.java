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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.startup.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class BootstrapTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    Path catalinaBase;

    @AfterEach
    void restoreStandardStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void delegatesLifecycleCommandsToCatalinaDaemonThroughBootstrap() throws Exception {
        Path serverXml = createServerXml();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> start = executor.submit(
                    () -> Bootstrap.main(new String[] {"-config", serverXml.toString(), "-nonaming", "start"}));
            start.get(10, TimeUnit.SECONDS);

            assertThat(System.getProperty("catalina.base")).isNotEmpty();
            Bootstrap.main(new String[] {"-config", serverXml.toString(), "-nonaming", "stopd"});
            Bootstrap.main(new String[] {"-config", serverXml.toString(), "-nonaming", "stop"});
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Path createServerXml() throws IOException {
        Files.createDirectories(catalinaBase.resolve("conf"));
        Files.createDirectories(catalinaBase.resolve("logs"));
        Files.createDirectories(catalinaBase.resolve("temp"));
        Files.createDirectories(catalinaBase.resolve("webapps"));
        Files.createDirectories(catalinaBase.resolve("work"));
        Path serverXml = catalinaBase.resolve("conf").resolve("server.xml");
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
