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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class JAXPDOMAdapterTest extends DocumentBuilderFactory {
    private static final String FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String FACTORY_CLASS_NAME = JAXPDOMAdapterTest.class.getName();

    private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();
    private String previousFactoryClassName;

    @BeforeEach
    void useAccessibleFactory() {
        previousFactoryClassName = System.getProperty(FACTORY_PROPERTY);
        System.setProperty(FACTORY_PROPERTY, FACTORY_CLASS_NAME);
    }

    @AfterEach
    void restoreFactory() {
        if (previousFactoryClassName == null) {
            System.clearProperty(FACTORY_PROPERTY);
        } else {
            System.setProperty(FACTORY_PROPERTY, previousFactoryClassName);
        }
    }

    @Test
    void createDocumentBuildsEmptyNamespaceAwareDomDocument() throws Exception {
        JAXPDOMAdapter adapter = new JAXPDOMAdapter();

        Document document = adapter.createDocument();
        Element root = document.createElementNS("urn:jdom-adapter-test", "sample:root");
        root.setAttributeNS("urn:jdom-adapter-test", "sample:id", "created");
        document.appendChild(root);

        assertThat(document.getDocumentElement()).isSameAs(root);
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-adapter-test");
        assertThat(root.getLocalName()).isEqualTo("root");
        assertThat(root.getAttributeNS("urn:jdom-adapter-test", "id")).isEqualTo("created");
    }

    @Test
    void getDocumentParsesNamespaceAwareXmlFromInputStream() throws Exception {
        JAXPDOMAdapter adapter = new JAXPDOMAdapter();
        String xml = """
                <sample:root xmlns:sample="urn:jdom-adapter-test" sample:id="parsed">
                    <sample:child>content</sample:child>
                </sample:root>
                """;

        Document document;
        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            document = adapter.getDocument(inputStream, false);
        }

        Element root = document.getDocumentElement();
        Element child = (Element) root.getElementsByTagNameNS("urn:jdom-adapter-test", "child").item(0);
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-adapter-test");
        assertThat(root.getLocalName()).isEqualTo("root");
        assertThat(root.getAttributeNS("urn:jdom-adapter-test", "id")).isEqualTo("parsed");
        assertThat(child.getTextContent()).isEqualTo("content");
    }

    @Override
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return new AccessibleDocumentBuilder(delegate.newDocumentBuilder());
    }

    @Override
    public void setNamespaceAware(boolean awareness) {
        delegate.setNamespaceAware(awareness);
    }

    @Override
    public void setValidating(boolean validating) {
        delegate.setValidating(validating);
    }

    @Override
    public void setIgnoringElementContentWhitespace(boolean whitespace) {
        delegate.setIgnoringElementContentWhitespace(whitespace);
    }

    @Override
    public void setExpandEntityReferences(boolean expandEntityRef) {
        delegate.setExpandEntityReferences(expandEntityRef);
    }

    @Override
    public void setIgnoringComments(boolean ignoreComments) {
        delegate.setIgnoringComments(ignoreComments);
    }

    @Override
    public void setCoalescing(boolean coalescing) {
        delegate.setCoalescing(coalescing);
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
    public boolean isIgnoringElementContentWhitespace() {
        return delegate.isIgnoringElementContentWhitespace();
    }

    @Override
    public boolean isExpandEntityReferences() {
        return delegate.isExpandEntityReferences();
    }

    @Override
    public boolean isIgnoringComments() {
        return delegate.isIgnoringComments();
    }

    @Override
    public boolean isCoalescing() {
        return delegate.isCoalescing();
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

    public static class AccessibleDocumentBuilder extends DocumentBuilder {
        private final DocumentBuilder delegate;

        public AccessibleDocumentBuilder(DocumentBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Document parse(InputSource is) throws SAXException, IOException {
            return delegate.parse(is);
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
        public void setEntityResolver(EntityResolver er) {
            delegate.setEntityResolver(er);
        }

        @Override
        public void setErrorHandler(ErrorHandler eh) {
            delegate.setErrorHandler(eh);
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
