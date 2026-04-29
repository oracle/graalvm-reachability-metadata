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
    void reportsUnknownOptionUsingBundledMessages() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedBytes = new ByteArrayOutputStream();

        try (PrintStream capturedErr = new PrintStream(capturedBytes, true, StandardCharsets.UTF_8)) {
            System.setErr(capturedErr);

            Report.unknownOption("strict-cleanup");
        } finally {
            System.setErr(originalErr);
        }

        String output = new String(capturedBytes.toByteArray(), StandardCharsets.UTF_8);
        assertThat(output).contains("Warning - unknown option: strict-cleanup");
    }
}
