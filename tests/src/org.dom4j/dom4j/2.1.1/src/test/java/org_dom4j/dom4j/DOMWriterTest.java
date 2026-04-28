/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.dom.DOMDocument;
import org.dom4j.io.DOMWriter;
import org.junit.jupiter.api.Test;

public class DOMWriterTest {
    @Test
    void resolvesDefaultAndNamedDomDocumentClasses() throws Exception {
        DOMWriter defaultWriter = new DOMWriter();

        Class<?> defaultDocumentClass = defaultWriter.getDomDocumentClass();

        assertThat(defaultDocumentClass).isNotNull();
        assertThat(org.w3c.dom.Document.class.isAssignableFrom(defaultDocumentClass))
                .isTrue();

        DOMWriter namedWriter = new DOMWriter();
        namedWriter.setDomDocumentClassName(DOMDocument.class.getName());

        assertThat(namedWriter.getDomDocumentClass()).isEqualTo(DOMDocument.class);
    }

    @Test
    void instantiatesConfiguredAndFallbackDomDocuments() throws Exception {
        org.w3c.dom.Document configuredDocument = new DOMWriter(DOMDocument.class)
                .write(sampleDocument());
        assertDomDocument(configuredDocument);

        org.w3c.dom.Document fallbackDocument = new FallbackDOMWriter()
                .write(sampleDocument());
        assertDomDocument(fallbackDocument);
    }

    private static Document sampleDocument() {
        Element root = DocumentHelper.createElement("root");
        root.addAttribute("id", "sample");
        root.addElement("child").addText("value");

        return DocumentHelper.createDocument(root);
    }

    private static void assertDomDocument(org.w3c.dom.Document document) {
        assertThat(document).isInstanceOf(DOMDocument.class);
        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("root");
        assertThat(document.getDocumentElement().getAttribute("id"))
                .isEqualTo("sample");
        assertThat(document.getDocumentElement().getFirstChild().getNodeName())
                .isEqualTo("child");
    }

    private static final class FallbackDOMWriter extends DOMWriter {
        @Override
        public Class<?> getDomDocumentClass() {
            return DOMDocument.class;
        }

        @Override
        protected org.w3c.dom.Document createDomDocumentViaJAXP() throws DocumentException {
            return null;
        }
    }
}
