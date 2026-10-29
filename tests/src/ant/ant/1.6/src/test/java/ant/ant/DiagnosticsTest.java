/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.tools.ant.Diagnostics;
import org.junit.jupiter.api.Test;

public class DiagnosticsTest {
    @Test
    void reportsTaskAvailabilityAndRuntimeDetails() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            Diagnostics.doReport(output);
        }
        String report = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Ant diagnostics report"));
        assertTrue(report.contains("core tasks"));
        assertTrue(report.contains("Tasks availability"));
        assertTrue(report.contains("XML Parser :"));
        assertTrue(report.contains("java.version"));
        assertFalse(report.contains("core tasks     : ?.?"));
        assertFalse(report.contains("None available"));
        assertFalse(report.contains("echo : "));
    }
}
