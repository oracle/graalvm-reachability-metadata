/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jtidy.jtidy;

import org.junit.jupiter.api.Test;
import org.w3c.tidy.Report;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportTest {
    @Test
    void showVersionLoadsMessageBundleDuringReportInitialization() {
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);

        Report.showVersion(writer);
        writer.flush();

        assertThat(output.toString())
                .contains("Java HTML Tidy release date:")
                .contains("See http://www.w3.org/People/Raggett for details");
    }
}
