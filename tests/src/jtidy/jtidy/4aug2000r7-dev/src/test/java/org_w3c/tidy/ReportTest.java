/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_w3c.tidy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.w3c.tidy.Node;
import org.w3c.tidy.Report;
import org.w3c.tidy.Tidy;

public class ReportTest {
    @Test
    void htmlParsingInitializesReportResourceBundle() {
        String html = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">"
                + "<html><head><title>JTidy resource report</title></head>"
                + "<body><p>Hello from JTidy</p></body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StringWriter reports = new StringWriter();

        Tidy tidy = new Tidy();
        tidy.setErrout(new PrintWriter(reports, true));

        Node document = tidy.parse(input, output);

        assertThat(document).isNotNull();
        assertThat(output.toString(StandardCharsets.ISO_8859_1)).contains("Hello from JTidy");
        assertThat(reports.toString())
                .contains("Parsing \"InputStream\"")
                .contains("Document content looks like");
    }

    @Test
    void generalInfoReadsMessageLoadedByReportInitializer() {
        StringWriter output = new StringWriter();

        Report.generalInfo(new PrintWriter(output, true));

        assertThat(output.toString())
                .contains("HTML & CSS specifications")
                .contains("http://www.w3.org/");
    }
}
