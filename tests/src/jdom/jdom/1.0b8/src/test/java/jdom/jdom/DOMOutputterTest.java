/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.output.DOMOutputter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DOMOutputterTest {
    private static final String DOCUMENT_BUILDER_FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";

    private static String previousDocumentBuilderFactory;

    @BeforeAll
    static void configureAccessibleJaxpFactory() {
        previousDocumentBuilderFactory = System.getProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
        System.setProperty(
                DOCUMENT_BUILDER_FACTORY_PROPERTY,
                JAXPDOMAdapterTest.AccessibleDocumentBuilderFactory.class.getName());
    }

    @AfterAll
    static void restoreJaxpFactory() {
        if (previousDocumentBuilderFactory == null) {
            System.clearProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
        } else {
            System.setProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY, previousDocumentBuilderFactory);
        }
    }

    @Test
    void outputsDocumentWithExplicitDomAdapterClass() throws Exception {
        DOMOutputter outputter = new DOMOutputter("org.jdom.adapters.JAXPDOMAdapter");
        Document document = createCatalogDocument();

        org.w3c.dom.Document domDocument = outputter.output(document);

        assertCatalogDocument(domDocument);
    }

    @Test
    void outputsDocumentWithJaxpAdapterDiscoveredByDefaultConstructor() throws Exception {
        DOMOutputter outputter = new DOMOutputter();
        Document document = createCatalogDocument();

        org.w3c.dom.Document domDocument = outputter.output(document);

        assertCatalogDocument(domDocument);
    }

    @Test
    void attemptsDefaultDomAdapterWhenExplicitAdapterClassIsUnavailable() throws Exception {
        DOMOutputter outputter = new DOMOutputter("missing.jdom.DomAdapter");
        Document document = createCatalogDocument();

        try {
            org.w3c.dom.Document domDocument = outputter.output(document);
            assertCatalogDocument(domDocument);
        } catch (JDOMException exception) {
            assertThat(exception).hasMessageContaining("Exception outputting Document");
        }
    }

    private static Document createCatalogDocument() {
        Namespace bookNamespace = Namespace.getNamespace("book", "urn:jdom:book");
        Element root = new Element("catalog", bookNamespace);
        root.setAttribute("id", "catalog-1");
        root.addContent(new Element("title", bookNamespace).setText("Native Image"));
        return new Document(root, new DocType("book:catalog"));
    }

    private static void assertCatalogDocument(org.w3c.dom.Document domDocument) {
        org.w3c.dom.Element root = domDocument.getDocumentElement();
        org.w3c.dom.Element title = (org.w3c.dom.Element) root.getElementsByTagNameNS(
                "urn:jdom:book", "title").item(0);

        assertThat(root.getTagName()).isEqualTo("book:catalog");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom:book");
        assertThat(root.getAttribute("id")).isEqualTo("catalog-1");
        assertThat(title.getTextContent()).isEqualTo("Native Image");
        assertThat(domDocument.getDoctype().getName()).isEqualTo("book:catalog");
    }
}
