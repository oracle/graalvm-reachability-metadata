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
import org.apache.tools.ant.Diagnostics;
import org.junit.jupiter.api.Test;

public class DiagnosticsTest {

    @Test
    void reportsRuntimeAndTaskAvailabilityDiagnostics() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            Diagnostics.doReport(stream);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Ant diagnostics report", "Tasks availability", "XML Parser information");
    }
}
