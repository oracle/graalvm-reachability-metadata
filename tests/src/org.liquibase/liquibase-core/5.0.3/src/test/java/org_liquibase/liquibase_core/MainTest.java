/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.integration.commandline.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void runParsesCommandLineAndDefaultPropertiesWithoutExecutingACommand() throws Exception {
        Path defaultsFile = tempDir.resolve("liquibase-main-defaults.properties");
        Files.writeString(defaultsFile, """
                url=jdbc:h2:mem:main_default_properties
                includeSchema=true
                parameter.applicationName=liquibase-main-test
                """);

        Main.setRunningFromNewCli(false);
        int exitCode = Main.run(new String[] {
                "--defaultsFile=" + defaultsFile,
                "--includeCatalog=true",
                "--outputDefaultSchema=false"
        });

        assertThat(exitCode).isZero();
    }
}
