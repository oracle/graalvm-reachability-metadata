/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.startup.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.init(new String[] {"-config", serverXml.toString(), "-nonaming", "start"});

        bootstrap.setAwait(false);
        assertThatExceptionOfType(NoSuchMethodException.class).isThrownBy(bootstrap::getAwait);

        bootstrap.start();
        assertThat(System.getProperty("catalina.base")).isNotEmpty();
        bootstrap.stopServer(new String[] {"stop"});
        bootstrap.stopServer();
        bootstrap.stop();

        Bootstrap.main(new String[] {"-config", serverXml.toString(), "-nonaming", "start"});
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
