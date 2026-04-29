/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jtidy.jtidy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.w3c.tidy.Report;
import org.w3c.tidy.Tidy;

public class ReportTest {
    @Test
    void printsVersionAfterLoadingReportMessages() {
        ByteArrayOutputStream capturedBytes = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(capturedBytes, true, StandardCharsets.UTF_8);

        Report.showVersion(writer);
        writer.flush();

        String output = new String(capturedBytes.toByteArray(), StandardCharsets.UTF_8);
        assertThat(output)
                .contains("Java HTML Tidy release date:")
                .contains("See http://www.w3.org/People/Raggett for details");
    }

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

    @Test
    void tidyParserUsesReportMessagesForMalformedHtml() {
        String malformedHtml = """
                <html>
                  <head><title>sample</title></head>
                  <body><p><img src=logo.png><font>content</body>
                </html>
                """;
        ByteArrayInputStream input = new ByteArrayInputStream(malformedHtml.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        Tidy tidy = new Tidy();
        tidy.setErrout(new PrintWriter(diagnostics, true, StandardCharsets.UTF_8));

        tidy.parse(input, output);

        String diagnosticOutput = new String(diagnostics.toByteArray(), StandardCharsets.UTF_8);
        assertThat(tidy.getParseWarnings()).isGreaterThan(0);
        assertThat(diagnosticOutput).contains("Warning:");
    }
}
