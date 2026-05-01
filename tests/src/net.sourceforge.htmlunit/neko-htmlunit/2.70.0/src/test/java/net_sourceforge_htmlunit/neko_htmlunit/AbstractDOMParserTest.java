/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.neko_htmlunit;

import java.io.StringReader;

import net.sourceforge.htmlunit.cyberneko.parsers.DOMParser;
import net.sourceforge.htmlunit.xerces.dom.DocumentImpl;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractDOMParserTest {

    @Test
    void parsesDocumentUsingConfiguredDocumentClass() throws Exception {
        DOMParser parser = new DOMParser(DocumentImpl.class);
        InputSource inputSource = new InputSource(new StringReader("""
                <!doctype html>
                <html>
                  <head><title>Example</title></head>
                  <body><p>Hello</p></body>
                </html>
                """));

        parser.parse(inputSource);

        Document document = parser.getDocument();
        assertThat(document).isInstanceOf(DocumentImpl.class);
        assertThat(document.getDocumentElement()).isNotNull();
        assertThat(document.getDocumentElement().getTextContent()).contains("Example", "Hello");
    }
}
