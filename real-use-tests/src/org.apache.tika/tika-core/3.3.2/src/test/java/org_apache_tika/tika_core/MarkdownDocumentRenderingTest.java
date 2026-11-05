/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.sax.ToMarkdownContentHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's document-to-Markdown rendering subsystem for a knowledge base import.
public class MarkdownDocumentRenderingTest {
    @Test
    @Timeout(60)
    public void rendersDocumentStructureAndLinksAsMarkdown() throws Exception {
        String document = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <h1>Release notes</h1>
                    <p>Use <a href="/guide">the guide</a>.</p>
                    <ul><li>First</li><li>Second</li></ul>
                    <strong>Important</strong>
                  </body>
                </html>
                """;
        ToMarkdownContentHandler handler = new ToMarkdownContentHandler();
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);

        parserFactory.newSAXParser().parse(new InputSource(new StringReader(document)), handler);

        String markdown = handler.toString().lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"));
        assertEquals("# Release notes\n\nUse [the guide](/guide).\n\n- First\n- Second\n**Important**",
                markdown);
    }
}
