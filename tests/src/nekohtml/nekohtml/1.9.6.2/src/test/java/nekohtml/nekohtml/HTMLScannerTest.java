/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.lang.reflect.Method;

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
import org.junit.jupiter.api.Test;

public class HTMLScannerTest {
    private static final String AUGMENTATIONS = "http://cyberneko.org/html/features/augmentations";

    @Test
    void compilerClassLiteralHelperResolvesClassesByName() throws Exception {
        Method classLiteralHelper = HTMLScanner.class.getDeclaredMethod("class$", String.class);
        classLiteralHelper.setAccessible(true);

        assertEquals(String.class, classLiteralHelper.invoke(null, String.class.getName()));
    }

    @Test
    void scanDocumentDispatchesStartDocumentThroughNamespaceAwareSignature() throws Exception {
        NamespaceAwareDocumentHandler handler = new NamespaceAwareDocumentHandler();
        HTMLScanner scanner = new HTMLScanner();
        XMLInputSource inputSource = new XMLInputSource(null, "memory:scanner.html", null,
                        new StringReader("<html><body>content</body></html>"), "UTF-8");

        scanner.setFeature(AUGMENTATIONS, true);
        scanner.setDocumentHandler(handler);
        scanner.setInputSource(inputSource);
        scanner.scanDocument(true);

        assertEquals(1, handler.namespaceAwareStartDocumentCount);
        assertEquals(0, handler.legacyStartDocumentCount);
        assertNotNull(handler.locator);
        assertNotNull(handler.namespaceContext);
        assertNotNull(handler.augmentations);
        assertEquals(1, handler.endDocumentCount);
    }

    public static class NamespaceAwareDocumentHandler implements XMLDocumentHandler {
        int namespaceAwareStartDocumentCount;
        int legacyStartDocumentCount;
        XMLLocator locator;
        NamespaceContext namespaceContext;
        Augmentations augmentations;
        int endDocumentCount;

        public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
                        Augmentations augmentations) {
            namespaceAwareStartDocumentCount++;
            this.locator = locator;
            this.namespaceContext = namespaceContext;
            this.augmentations = augmentations;
        }

        @Override
        public void startDocument(XMLLocator locator, String encoding, Augmentations augmentations) {
            legacyStartDocumentCount++;
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
        }
    }
}
