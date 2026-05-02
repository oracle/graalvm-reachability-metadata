/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.tools.ant.Diagnostics;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DiagnosticsTest {
    @Test
    void reportsImplementationVersionsAndTaskAvailabilityFromPublicDiagnostics() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            try {
                Diagnostics.validateVersion();
                Diagnostics.doReport(stream);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
                return;
            }
        }

        String report = output.toString(StandardCharsets.UTF_8);

        assertThat(report)
                .contains("------- Ant diagnostics report -------")
                .contains("Implementation Version")
                .contains("core tasks     :")
                .contains("optional tasks :")
                .contains("Tasks availability")
                .doesNotContain("None available");
        assertThat(Diagnostics.isOptionalAvailable()).isTrue();
    }
}
