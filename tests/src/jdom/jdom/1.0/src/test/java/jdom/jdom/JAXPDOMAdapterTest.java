/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jdom.adapters.JAXPDOMAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class JAXPDOMAdapterTest {
    private static final String DOCUMENT_BUILDER_FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";

    private static String previousDocumentBuilderFactory;

    @BeforeAll
    static void configureAccessibleJaxpFactory() {
        previousDocumentBuilderFactory = System.getProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
        System.setProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY, AccessibleDocumentBuilderFactory.class.getName());
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
    void parsesNamespaceAwareDocumentThroughJaxpReflection() throws Exception {
        JAXPDOMAdapter adapter = new JAXPDOMAdapter();
        String xml = """
                <root xmlns="urn:jdom-test" xmlns:item="urn:jdom-item">
                    <item:child id="c1">value</item:child>
                </root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, false);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagNameNS("urn:jdom-item", "child").item(0);
            assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-test");
            assertThat(child.getAttribute("id")).isEqualTo("c1");
            assertThat(child.getTextContent()).isEqualTo("value");
        }
    }

    @Test
    void createsMutableDomDocumentThroughJaxpReflection() throws Exception {
        JAXPDOMAdapter adapter = new JAXPDOMAdapter();

        Document document = adapter.createDocument();
        Element root = document.createElementNS("urn:jdom-created", "created:root");
        root.setAttribute("source", "jaxp");
        document.appendChild(root);

        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("urn:jdom-created");
        assertThat(document.getDocumentElement().getAttribute("source")).isEqualTo("jaxp");
    }

    public static class AccessibleDocumentBuilderFactory extends DocumentBuilderFactory {
        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            delegate.setValidating(isValidating());
            delegate.setNamespaceAware(isNamespaceAware());
            delegate.setIgnoringElementContentWhitespace(isIgnoringElementContentWhitespace());
            delegate.setExpandEntityReferences(isExpandEntityReferences());
            delegate.setIgnoringComments(isIgnoringComments());
            delegate.setCoalescing(isCoalescing());
            return new AccessibleDocumentBuilder(delegate.newDocumentBuilder());
        }

        @Override
        public void setAttribute(String name, Object value) throws IllegalArgumentException {
            delegate.setAttribute(name, value);
        }

        @Override
        public Object getAttribute(String name) throws IllegalArgumentException {
            return delegate.getAttribute(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws ParserConfigurationException {
            delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name) throws ParserConfigurationException {
            return delegate.getFeature(name);
        }
    }

    public static class AccessibleDocumentBuilder extends DocumentBuilder {
        private final DocumentBuilder delegate;

        public AccessibleDocumentBuilder(DocumentBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Document parse(InputSource inputSource) throws SAXException, IOException {
            return delegate.parse(inputSource);
        }

        @Override
        public boolean isNamespaceAware() {
            return delegate.isNamespaceAware();
        }

        @Override
        public boolean isValidating() {
            return delegate.isValidating();
        }

        @Override
        public void setEntityResolver(EntityResolver entityResolver) {
            delegate.setEntityResolver(entityResolver);
        }

        @Override
        public void setErrorHandler(ErrorHandler errorHandler) {
            delegate.setErrorHandler(errorHandler);
        }

        @Override
        public Document newDocument() {
            return delegate.newDocument();
        }

        @Override
        public DOMImplementation getDOMImplementation() {
            return delegate.getDOMImplementation();
        }
    }
}
