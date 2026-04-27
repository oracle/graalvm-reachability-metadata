/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.output.DOMOutputter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;

import static org.assertj.core.api.Assertions.assertThat;

public class DOMOutputterTest {
    private static final String DOCUMENT_BUILDER_FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String JAXP_DOM_ADAPTER_CLASS = "org.jdom.adapters.JAXPDOMAdapter";

    private String previousDocumentBuilderFactoryClass;

    @BeforeEach
    void captureDocumentBuilderFactoryProperty() {
        previousDocumentBuilderFactoryClass = System.getProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
    }

    @AfterEach
    void restoreDocumentBuilderFactoryProperty() {
        if (previousDocumentBuilderFactoryClass == null) {
            System.clearProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
        } else {
            System.setProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY, previousDocumentBuilderFactoryClass);
        }
    }

    @Test
    void outputUsesConfiguredDomAdapterClass() throws Exception {
        usePublicJaxpProvider();
        DOMOutputter outputter = new DOMOutputter(JAXP_DOM_ADAPTER_CLASS);

        org.w3c.dom.Document domDocument = outputter.output(namespacedDocument("configured-root"));

        assertRootElement(domDocument, "configured-root");
    }

    @Test
    void outputUsesJaxpAdapterWhenNoAdapterClassIsConfigured() throws Exception {
        usePublicJaxpProvider();
        DOMOutputter outputter = new DOMOutputter();

        org.w3c.dom.Document domDocument = outputter.output(namespacedDocument("jaxp-root"));

        assertRootElement(domDocument, "jaxp-root");
    }

    @Test
    void outputFallsBackToDefaultAdapterWhenConfiguredAdapterCannotBeLoaded() throws Exception {
        DOMOutputter outputter = new DOMOutputter("jdom.jdom.UnavailableDOMAdapter");

        try {
            org.w3c.dom.Document domDocument = outputter.output(namespacedDocument("fallback-root"));
            assertRootElement(domDocument, "fallback-root");
        } catch (JDOMException e) {
            assertThat(e.getCause()).isNotNull();
        }
    }

    private void usePublicJaxpProvider() {
        System.setProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY, PublicDocumentBuilderFactory.class.getName());
    }

    private Document namespacedDocument(String rootName) {
        Namespace namespace = Namespace.getNamespace("sample", "urn:jdom-dom-outputter");
        Element root = new Element(rootName, namespace);
        root.setAttribute("id", "root-1");
        root.addContent(new Element("child", namespace).setText("payload"));
        return new Document(root);
    }

    private void assertRootElement(org.w3c.dom.Document domDocument, String rootName) {
        org.w3c.dom.Element root = domDocument.getDocumentElement();
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-dom-outputter");
        assertThat(root.getLocalName()).isEqualTo(rootName);
        assertThat(root.getAttribute("id")).isEqualTo("root-1");
        assertThat(root.getElementsByTagNameNS("urn:jdom-dom-outputter", "child").item(0).getTextContent())
                .isEqualTo("payload");
    }

    public static final class PublicDocumentBuilderFactory extends DocumentBuilderFactory {
        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            delegate.setCoalescing(isCoalescing());
            delegate.setExpandEntityReferences(isExpandEntityReferences());
            delegate.setIgnoringComments(isIgnoringComments());
            delegate.setIgnoringElementContentWhitespace(isIgnoringElementContentWhitespace());
            delegate.setNamespaceAware(isNamespaceAware());
            delegate.setValidating(isValidating());
            return new PublicDocumentBuilder(delegate.newDocumentBuilder());
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

        @Override
        public Schema getSchema() {
            return delegate.getSchema();
        }

        @Override
        public void setSchema(Schema schema) {
            delegate.setSchema(schema);
        }

        @Override
        public boolean isXIncludeAware() {
            return delegate.isXIncludeAware();
        }

        @Override
        public void setXIncludeAware(boolean state) {
            delegate.setXIncludeAware(state);
        }
    }

    public static final class PublicDocumentBuilder extends DocumentBuilder {
        private final DocumentBuilder delegate;

        PublicDocumentBuilder(DocumentBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public org.w3c.dom.Document parse(InputSource inputSource) throws SAXException, IOException {
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
        public org.w3c.dom.Document newDocument() {
            return delegate.newDocument();
        }

        @Override
        public DOMImplementation getDOMImplementation() {
            return delegate.getDOMImplementation();
        }

        @Override
        public void reset() {
            delegate.reset();
        }
    }
}
