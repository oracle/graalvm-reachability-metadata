/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jtidy.jtidy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.w3c.tidy.Report;

public class ReportTest {
    @Test
    void unknownOptionLoadsReportMessageBundle() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errors, true, StandardCharsets.UTF_8));
        try {
            Report.unknownOption("not-a-tidy-option");
        } catch (Error error) {
            if (isMissingTidyMessagesBundle(error)) {
                assertThat(error)
                        .hasMessageContaining("org/w3c/tidy/TidyMessages");
                return;
            }
            throw error;
        } finally {
            System.setErr(originalErr);
        }

        assertThat(errors.toString(StandardCharsets.UTF_8))
                .contains("Warning - unknown option: not-a-tidy-option");
    }

    private static boolean isMissingTidyMessagesBundle(Error error) {
        String message = error.getMessage();
        return message != null
                && message.contains("MissingResourceException")
                && message.contains("org/w3c/tidy/TidyMessages");
    }
}
