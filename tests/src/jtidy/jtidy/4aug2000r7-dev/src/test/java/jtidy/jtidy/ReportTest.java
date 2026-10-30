/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jtidy.jtidy;

import org.junit.jupiter.api.Test;
import org.w3c.tidy.Tidy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportTest {
    @Test
    void reportsMissingTitleWhileRepairingHtml() {
        Tidy tidy = new Tidy();
        StringWriter diagnostics = new StringWriter();
        tidy.setErrout(new PrintWriter(diagnostics, true));
        tidy.setQuiet(true);
        tidy.setShowWarnings(true);
        tidy.setTidyMark(false);
        String html = """
                <html><head></head><body><p>Library diagnostics</p></body></html>
                """;
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes(StandardCharsets.US_ASCII));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertNotNull(tidy.parse(input, output));

        assertEquals(0, tidy.getParseErrors());
        assertTrue(tidy.getParseWarnings() > 0);
        assertTrue(diagnostics.toString().contains("Warning: inserting missing 'title' element"),
                diagnostics.toString());
        String repaired = output.toString(StandardCharsets.US_ASCII);
        assertTrue(repaired.contains("<title></title>"), repaired);
        assertTrue(repaired.contains("<p>Library diagnostics</p>"), repaired);
    }
}
