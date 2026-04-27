/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.adapters.JAXPDOMAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;

import static org.assertj.core.api.Assertions.assertThat;

public class JAXPDOMAdapterTest {
    private static final String DOCUMENT_BUILDER_FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";

    private String previousDocumentBuilderFactoryClass;

    @AfterEach
    void restoreDocumentBuilderFactoryProperty() {
        if (previousDocumentBuilderFactoryClass == null) {
            System.clearProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
        } else {
            System.setProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY, previousDocumentBuilderFactoryClass);
        }
    }

    @Test
    void createDocumentReturnsMutableDomDocument() throws Exception {
        JAXPDOMAdapter adapter = newAdapterUsingPublicJaxpProvider();

        Document document = adapter.createDocument();
        Element root = document.createElementNS("urn:jdom-adapter", "adapter:root");
        root.setAttribute("id", "created");
        document.appendChild(root);

        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("urn:jdom-adapter");
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("root");
        assertThat(document.getDocumentElement().getAttribute("id")).isEqualTo("created");
    }

    @Test
    void getDocumentParsesNamespacedInputStream() throws Exception {
        JAXPDOMAdapter adapter = newAdapterUsingPublicJaxpProvider();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <adapter:root xmlns:adapter="urn:jdom-adapter">
                  <adapter:child name="parsed">text</adapter:child>
                </adapter:root>
                """;

        Document document = adapter.getDocument(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), false);
        Element root = document.getDocumentElement();
        Element child = (Element) root.getElementsByTagNameNS("urn:jdom-adapter", "child").item(0);

        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-adapter");
        assertThat(root.getLocalName()).isEqualTo("root");
        assertThat(child.getAttribute("name")).isEqualTo("parsed");
        assertThat(child.getTextContent()).isEqualTo("text");
    }

    private JAXPDOMAdapter newAdapterUsingPublicJaxpProvider() {
        previousDocumentBuilderFactoryClass = System.getProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY);
        System.setProperty(DOCUMENT_BUILDER_FACTORY_PROPERTY, PublicDocumentBuilderFactory.class.getName());
        return new JAXPDOMAdapter();
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

        @Override
        public void reset() {
            delegate.reset();
        }
    }
}
