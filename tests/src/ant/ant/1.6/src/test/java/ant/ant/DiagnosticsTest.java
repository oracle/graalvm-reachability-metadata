/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.ant.Diagnostics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DiagnosticsTest {
    @Test
    void reportsImplementationVersionsTaskAvailabilityAndRuntimeEnvironment(@TempDir Path antHome) throws Exception {
        Path libDirectory = antHome.resolve("lib");
        Files.createDirectories(libDirectory);
        Files.write(libDirectory.resolve("diagnostics-fixture.jar"), new byte[] {1, 2, 3});

        String previousAntHome = System.getProperty("ant.home");
        System.setProperty("ant.home", antHome.toString());
        try {
            Diagnostics.validateVersion();

            ByteArrayOutputStream reportBuffer = new ByteArrayOutputStream();
            try (PrintStream reportStream = new PrintStream(reportBuffer, true, StandardCharsets.UTF_8)) {
                Diagnostics.doReport(reportStream);
            }

            String report = reportBuffer.toString(StandardCharsets.UTF_8);
            assertThat(report).contains("------- Ant diagnostics report -------");
            assertThat(report).contains("Implementation Version (JDK1.2+ only)");
            assertThat(report).contains("core tasks     :");
            assertThat(report).contains("Tasks availability");
            assertThat(report).contains("diagnostics-fixture.jar (3 bytes)");
            assertThat(report).contains("XML Parser :");
            assertThat(report).contains("System properties");
            assertThat(report).doesNotContain("None available");
        } finally {
            if (previousAntHome == null) {
                System.clearProperty("ant.home");
            } else {
                System.setProperty("ant.home", previousAntHome);
            }
        }
    }
}
