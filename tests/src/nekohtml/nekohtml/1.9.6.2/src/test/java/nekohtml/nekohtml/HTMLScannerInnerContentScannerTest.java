/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.StringReader;

import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLDocumentHandler;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.cyberneko.html.HTMLScanner;
import org.cyberneko.html.filters.DefaultFilter;
import org.junit.jupiter.api.Test;

public class HTMLScannerInnerContentScannerTest {
    private static final String AUGMENTATIONS = "http://cyberneko.org/html/features/augmentations";

    @Test
    void startDocumentUsesNamespaceAwareReflectiveDispatch() throws Exception {
        NamespaceAwareDocumentHandler handler = new NamespaceAwareDocumentHandler();

        parseWithScanner(handler, "<html><body>content</body></html>");

        assertEquals(1, handler.startDocumentCount);
        assertNotNull(handler.locator);
        assertNotNull(handler.namespaceContext);
        assertNotNull(handler.startDocumentAugmentations);
        assertEquals(1, handler.endDocumentCount);
        assertSame(handler.startDocumentAugmentations, handler.endDocumentAugmentations);
    }

    @Test
    void startDocumentFallsBackToLegacyReflectiveDispatch() throws Exception {
        LegacyDocumentHandler handler = new LegacyDocumentHandler();

        parseWithScanner(handler, "<html><body>legacy</body></html>");

        assertEquals(1, handler.startDocumentCount);
        assertNotNull(handler.locator);
        assertNotNull(handler.startDocumentAugmentations);
        assertEquals(1, handler.endDocumentCount);
        assertSame(handler.startDocumentAugmentations, handler.endDocumentAugmentations);
    }

    private static void parseWithScanner(XMLDocumentHandler handler, String html) throws Exception {
        HTMLScanner scanner = new HTMLScanner();
        XMLInputSource inputSource = new XMLInputSource(null, "memory:content-scanner.html", null,
                        new StringReader(html), "UTF-8");

        scanner.setFeature(AUGMENTATIONS, true);
        scanner.setDocumentHandler(handler);
        scanner.setInputSource(inputSource);
        scanner.scanDocument(true);
    }

    public static class NamespaceAwareDocumentHandler extends DefaultFilter {
        int startDocumentCount;
        XMLLocator locator;
        NamespaceContext namespaceContext;
        Augmentations startDocumentAugmentations;
        int endDocumentCount;
        Augmentations endDocumentAugmentations;

        @Override
        public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
                        Augmentations augmentations) {
            startDocumentCount++;
            this.locator = locator;
            this.namespaceContext = namespaceContext;
            this.startDocumentAugmentations = augmentations;
        }

        @Override
        public void endDocument(Augmentations augmentations) {
            endDocumentCount++;
            endDocumentAugmentations = augmentations;
        }
    }

    public static class LegacyDocumentHandler implements XMLDocumentHandler {
        int startDocumentCount;
        XMLLocator locator;
        Augmentations startDocumentAugmentations;
        int endDocumentCount;
        Augmentations endDocumentAugmentations;

        @Override
        public void startDocument(XMLLocator locator, String encoding, Augmentations augmentations) {
            startDocumentCount++;
            this.locator = locator;
            this.startDocumentAugmentations = augmentations;
        }

        @Override
        public void xmlDecl(String version, String encoding, String standalone, Augmentations augmentations) {
        }

        @Override
        public void doctypeDecl(String rootElement, String publicId, String systemId, Augmentations augmentations) {
        }

        @Override
        public void comment(XMLString text, Augmentations augmentations) {
        }

        @Override
        public void processingInstruction(String target, XMLString data, Augmentations augmentations) {
        }

        @Override
        public void startPrefixMapping(String prefix, String uri, Augmentations augmentations) {
        }

        @Override
        public void startElement(QName element, XMLAttributes attributes, Augmentations augmentations) {
        }

        @Override
        public void emptyElement(QName element, XMLAttributes attributes, Augmentations augmentations) {
        }

        @Override
        public void startGeneralEntity(String name, XMLResourceIdentifier identifier, String encoding,
                        Augmentations augmentations) {
        }

        @Override
        public void textDecl(String version, String encoding, Augmentations augmentations) {
        }

        @Override
        public void endGeneralEntity(String name, Augmentations augmentations) {
        }

        @Override
        public void characters(XMLString text, Augmentations augmentations) {
        }

        @Override
        public void ignorableWhitespace(XMLString text, Augmentations augmentations) {
        }

        @Override
        public void endElement(QName element, Augmentations augmentations) {
        }

        @Override
        public void endPrefixMapping(String prefix, Augmentations augmentations) {
        }

        @Override
        public void startCDATA(Augmentations augmentations) {
        }

        @Override
        public void endCDATA(Augmentations augmentations) {
        }

        @Override
        public void endDocument(Augmentations augmentations) {
            endDocumentCount++;
            endDocumentAugmentations = augmentations;
        }
    }
}
