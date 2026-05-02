/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.Document;
import org.dom4j.io.SAXContentHandler;
import org.junit.jupiter.api.Test;
import org.xml.sax.ext.Locator2Impl;
import org.xml.sax.helpers.AttributesImpl;

public class SAXContentHandlerTest {
    @Test
    void createsDocumentWithEncodingFromSaxLocator() throws Exception {
        Locator2Impl locator = new Locator2Impl();
        locator.setEncoding("UTF-8");
        SAXContentHandler handler = new SAXContentHandler();
        handler.setDocumentLocator(locator);

        String text = "content";
        handler.startDocument();
        handler.startElement("", "root", "root", new AttributesImpl());
        handler.characters(text.toCharArray(), 0, text.length());
        handler.endElement("", "root", "root");
        handler.endDocument();

        Document document = handler.getDocument();
        assertThat(document.getXMLEncoding()).isEqualTo("UTF-8");
        assertThat(document.getRootElement().getName()).isEqualTo("root");
        assertThat(document.getRootElement().getText()).isEqualTo("content");
    }
}
