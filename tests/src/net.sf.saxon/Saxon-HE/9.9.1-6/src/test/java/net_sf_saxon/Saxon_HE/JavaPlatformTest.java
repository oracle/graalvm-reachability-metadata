/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import java.io.StringReader;

import net.sf.saxon.java.JavaPlatform;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaPlatformTest {
    private static final String EXTERNAL_ENTITY_TEXT = "resolved entity text";
    private static final String SAX_PARSER_FACTORY_PROPERTY = "javax.xml.parsers.SAXParserFactory";

    @Test
    void loadParserForXmlFragmentsBypassesJaxpFactoryAndCreatesUsableXmlReader() throws Exception {
        JavaPlatform platform = new JavaPlatform();
        String originalFactory = System.getProperty(SAX_PARSER_FACTORY_PROPERTY);
        System.setProperty(SAX_PARSER_FACTORY_PROPERTY, "example.MissingSaxParserFactory");
        try {
            XMLReader reader = platform.loadParserForXmlFragments();
            CapturingContentHandler contentHandler = new CapturingContentHandler();
            reader.setContentHandler(contentHandler);
            reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader(EXTERNAL_ENTITY_TEXT)));

            reader.parse(new InputSource(new StringReader("""
                    <!DOCTYPE root [<!ENTITY external SYSTEM "memory:external-entity">]>
                    <root>&external;</root>
                    """)));

            assertThat(reader).isNotNull();
            assertThat(contentHandler.characters()).isEqualTo(EXTERNAL_ENTITY_TEXT);
        } finally {
            restoreSaxParserFactoryProperty(originalFactory);
        }
    }

    private static void restoreSaxParserFactoryProperty(String originalFactory) {
        if (originalFactory == null) {
            System.clearProperty(SAX_PARSER_FACTORY_PROPERTY);
        } else {
            System.setProperty(SAX_PARSER_FACTORY_PROPERTY, originalFactory);
        }
    }

    private static final class CapturingContentHandler extends DefaultHandler {
        private final StringBuilder characters = new StringBuilder();

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            characters.append(ch, start, length);
        }

        private String characters() {
            return characters.toString().trim();
        }
    }
}
