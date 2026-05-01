/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.jdom.output.DOMOutputter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMImplementation;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DOMOutputterTest extends DocumentBuilderFactory {
    private static final String FACTORY_PROPERTY = "javax.xml.parsers.DocumentBuilderFactory";
    private static final String FACTORY_CLASS_NAME = DOMOutputterTest.class.getName();
    private static final String JAXP_ADAPTER_CLASS_NAME = "org.jdom.adapters.JAXPDOMAdapter";
    private static final String MISSING_ADAPTER_CLASS_NAME = "example.missing.DOMAdapter";

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
    void outputWithExplicitJaxpAdapterCreatesDomDocument() throws Exception {
        DOMOutputter outputter = new DOMOutputter(JAXP_ADAPTER_CLASS_NAME);
        Document document = new Document(createSampleRoot());

        org.w3c.dom.Document domDocument = outputter.output(document);

        org.w3c.dom.Element root = domDocument.getDocumentElement();
        org.w3c.dom.Element child = (org.w3c.dom.Element) root.getFirstChild();
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-dom-outputter-test:root");
        assertThat(root.getLocalName()).isEqualTo("root");
        assertThat(root.getAttributeNS("urn:jdom-dom-outputter-test:attribute", "id")).isEqualTo("42");
        assertThat(child.getNamespaceURI()).isEqualTo("urn:jdom-dom-outputter-test:child");
        assertThat(child.getTextContent()).isEqualTo("content");
    }

    @Test
    void outputWithDefaultAdapterUsesJaxpAdapter() throws Exception {
        DOMOutputter outputter = new DOMOutputter();
        Document document = new Document(createSampleRoot());
        document.addContent(new ProcessingInstruction("sample-target", "sample-data"));

        org.w3c.dom.Document domDocument = outputter.output(document);

        assertThat(domDocument.getDocumentElement().getLocalName()).isEqualTo("root");
        assertThat(domDocument.getLastChild().getNodeType()).isEqualTo(org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE);
        assertThat(domDocument.getLastChild().getNodeName()).isEqualTo("sample-target");
    }

    @Test
    void outputFallsBackToDefaultAdapterWhenConfiguredAdapterIsUnavailable() {
        DOMOutputter outputter = new DOMOutputter(MISSING_ADAPTER_CLASS_NAME);
        Document document = new Document(new Element("fallback-root"));

        assertThatThrownBy(() -> outputter.output(document))
                .isInstanceOf(JDOMException.class)
                .hasRootCauseInstanceOf(ClassNotFoundException.class)
                .hasMessageContaining("org.apache.xerces.dom.DocumentImpl");
    }

    private static Element createSampleRoot() {
        Namespace rootNamespace = Namespace.getNamespace("sample", "urn:jdom-dom-outputter-test:root");
        Namespace childNamespace = Namespace.getNamespace("child", "urn:jdom-dom-outputter-test:child");
        Namespace attributeNamespace = Namespace.getNamespace("attr", "urn:jdom-dom-outputter-test:attribute");

        Element root = new Element("root", rootNamespace);
        root.setAttribute("id", "42", attributeNamespace);
        root.addContent(new Element("child", childNamespace).setText("content"));
        return root;
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
        public org.w3c.dom.Document parse(InputSource is) throws SAXException, IOException {
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
        public org.w3c.dom.Document newDocument() {
            return delegate.newDocument();
        }

        @Override
        public DOMImplementation getDOMImplementation() {
            return delegate.getDOMImplementation();
        }
    }
}
