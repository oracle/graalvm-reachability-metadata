/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Tika's SAX XPath selection subsystem for extracting an article body.
public class StructuredContentSelectionTest {
    @Test
    @Timeout(60)
    public void selectsOnlyArticleParagraphsFromAnXhtmlDocument() throws Exception {
        String document = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <nav><p>Navigation text</p></nav>
                    <article>
                      <p>Release notes for the import service.</p>
                      <p>It now accepts signed documents.</p>
                    </article>
                  </body>
                </html>
                """;
        ToTextContentHandler textHandler = new ToTextContentHandler();
        XPathParser xpath = new XPathParser("xhtml", "http://www.w3.org/1999/xhtml");
        MatchingContentHandler selection = new MatchingContentHandler(textHandler,
                xpath.parse("/xhtml:html/xhtml:body/xhtml:article/xhtml:p/text()"));
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);

        parserFactory.newSAXParser().parse(new InputSource(new StringReader(document)), selection);

        assertEquals("Release notes for the import service.It now accepts signed documents.",
                textHandler.toString());
    }
}
