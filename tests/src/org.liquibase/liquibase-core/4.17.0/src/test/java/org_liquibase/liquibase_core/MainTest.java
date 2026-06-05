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

public class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesCommandLineOptionsAndDefaultsFile() throws Exception {
        Path defaultsFile = tempDir.resolve("liquibase-defaults.properties");
        Files.writeString(defaultsFile, """
                url=jdbc:h2:mem:liquibaseCliDefaults
                includeCatalog=true
                parameter.applicationName=coverage-test
                """);

        int exitCode = Main.run(new String[] {
                "--defaultsFile=" + defaultsFile,
                "--logLevel=off",
                "--promptForNonLocalDatabase=false"
        });

        assertThat(exitCode).isZero();
    }
}
