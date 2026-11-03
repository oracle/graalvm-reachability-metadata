/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Tika's SAX link extraction subsystem for an XHTML document catalog.
public class DocumentLinkExtractionTest {
    @Test
    @Timeout(60)
    public void extractsAnchorsAndImagesFromAnXhtmlPage() throws Exception {
        String page = """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body>
                    <a href="/guides/import" title="Import guide" rel="help">Import guide</a>
                    <img src="/images/import.png" title="Import" alt="Import diagram"/>
                  </body>
                </html>
                """;
        LinkContentHandler handler = new LinkContentHandler(true);
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        parserFactory.newSAXParser().parse(new InputSource(new StringReader(page)), handler);

        List<Link> links = handler.getLinks();
        assertEquals(2, links.size());

        Link anchor = links.get(0);
        assertTrue(anchor.isAnchor());
        assertEquals("/guides/import", anchor.getUri());
        assertEquals("Import guide", anchor.getTitle());
        assertEquals("help", anchor.getRel());
        assertEquals("Import guide", anchor.getText());

        Link image = links.get(1);
        assertTrue(image.isImage());
        assertEquals("/images/import.png", image.getUri());
        assertEquals("Import", image.getTitle());
        assertEquals("Import diagram", image.getText());
    }
}
