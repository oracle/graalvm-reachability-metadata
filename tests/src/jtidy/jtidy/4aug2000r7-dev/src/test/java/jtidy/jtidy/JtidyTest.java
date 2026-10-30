/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jtidy.jtidy;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JtidyTest {
    @Test
    void parsesHtmlIntoDom() {
        Tidy tidy = new Tidy();
        tidy.setErrout(new PrintWriter(new StringWriter()));
        tidy.setQuiet(true);
        String html = """
                <html><head><title>Document title</title></head>
                <body><p>Fish &amp; chips</p></body></html>
                """;
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes(StandardCharsets.US_ASCII));

        Document document = tidy.parseDOM(input, null);

        assertNotNull(document);
        assertEquals(0, tidy.getParseErrors());
        assertEquals("html", document.getDocumentElement().getTagName());
        assertEquals("Document title", document.getElementsByTagName("title").item(0).getFirstChild().getNodeValue());
        assertEquals("Fish & chips", document.getElementsByTagName("p").item(0).getFirstChild().getNodeValue());
    }
}
