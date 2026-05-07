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
import java.util.concurrent.atomic.AtomicInteger;

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
        AccessibleDocumentBuilderFactory.resetProbe();
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
            assertGetDocumentReflectionPathReached();
        }
    }

    @Test
    void parsesValidatingDocumentThroughJaxpReflection() throws Exception {
        AccessibleDocumentBuilderFactory.resetProbe();
        JAXPDOMAdapter adapter = new JAXPDOMAdapter();
        String xml = """
                <!DOCTYPE root [
                <!ELEMENT root (child)>
                <!ELEMENT child (#PCDATA)>
                <!ATTLIST child id ID #REQUIRED>
                ]>
                <root><child id="c1">validated</child></root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, true);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagName("child").item(0);
            assertThat(document.getDoctype().getName()).isEqualTo("root");
            assertThat(child.getAttribute("id")).isEqualTo("c1");
            assertThat(child.getTextContent()).isEqualTo("validated");
            assertGetDocumentReflectionPathReached();
        }
    }

    @Test
    void createsMutableDomDocumentThroughJaxpReflection() throws Exception {
        AccessibleDocumentBuilderFactory.resetProbe();
        JAXPDOMAdapter adapter = new JAXPDOMAdapter();

        Document document = adapter.createDocument();
        Element root = document.createElementNS("urn:jdom-created", "created:root");
        root.setAttribute("source", "jaxp");
        document.appendChild(root);

        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("urn:jdom-created");
        assertThat(document.getDocumentElement().getAttribute("source")).isEqualTo("jaxp");
        assertThat(AccessibleDocumentBuilderFactory.newDocumentBuilderCalls()).isEqualTo(1);
        assertThat(AccessibleDocumentBuilderFactory.newDocumentCalls()).isEqualTo(1);
    }

    private static void assertGetDocumentReflectionPathReached() {
        assertThat(AccessibleDocumentBuilderFactory.newDocumentBuilderCalls()).isEqualTo(1);
        assertThat(AccessibleDocumentBuilderFactory.setValidatingCalls()).isEqualTo(1);
        assertThat(AccessibleDocumentBuilderFactory.setNamespaceAwareCalls()).isEqualTo(1);
        assertThat(AccessibleDocumentBuilderFactory.setErrorHandlerCalls()).isEqualTo(1);
        assertThat(AccessibleDocumentBuilderFactory.parseInputStreamCalls()).isEqualTo(1);
    }

    public static class AccessibleDocumentBuilderFactory extends DocumentBuilderFactory {
        private static final AtomicInteger NEW_DOCUMENT_BUILDER_CALLS = new AtomicInteger();
        private static final AtomicInteger SET_VALIDATING_CALLS = new AtomicInteger();
        private static final AtomicInteger SET_NAMESPACE_AWARE_CALLS = new AtomicInteger();
        private static final AtomicInteger SET_ERROR_HANDLER_CALLS = new AtomicInteger();
        private static final AtomicInteger PARSE_INPUT_STREAM_CALLS = new AtomicInteger();
        private static final AtomicInteger NEW_DOCUMENT_CALLS = new AtomicInteger();

        private final DocumentBuilderFactory delegate = DocumentBuilderFactory.newDefaultInstance();

        static void resetProbe() {
            NEW_DOCUMENT_BUILDER_CALLS.set(0);
            SET_VALIDATING_CALLS.set(0);
            SET_NAMESPACE_AWARE_CALLS.set(0);
            SET_ERROR_HANDLER_CALLS.set(0);
            PARSE_INPUT_STREAM_CALLS.set(0);
            NEW_DOCUMENT_CALLS.set(0);
        }

        static int newDocumentBuilderCalls() {
            return NEW_DOCUMENT_BUILDER_CALLS.get();
        }

        static int setValidatingCalls() {
            return SET_VALIDATING_CALLS.get();
        }

        static int setNamespaceAwareCalls() {
            return SET_NAMESPACE_AWARE_CALLS.get();
        }

        static int setErrorHandlerCalls() {
            return SET_ERROR_HANDLER_CALLS.get();
        }

        static int parseInputStreamCalls() {
            return PARSE_INPUT_STREAM_CALLS.get();
        }

        static int newDocumentCalls() {
            return NEW_DOCUMENT_CALLS.get();
        }

        @Override
        public void setValidating(boolean validating) {
            SET_VALIDATING_CALLS.incrementAndGet();
            super.setValidating(validating);
        }

        @Override
        public void setNamespaceAware(boolean awareness) {
            SET_NAMESPACE_AWARE_CALLS.incrementAndGet();
            super.setNamespaceAware(awareness);
        }

        @Override
        public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
            NEW_DOCUMENT_BUILDER_CALLS.incrementAndGet();
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
        public Document parse(InputStream inputStream) throws SAXException, IOException {
            AccessibleDocumentBuilderFactory.PARSE_INPUT_STREAM_CALLS.incrementAndGet();
            return delegate.parse(inputStream);
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
            AccessibleDocumentBuilderFactory.SET_ERROR_HANDLER_CALLS.incrementAndGet();
            delegate.setErrorHandler(errorHandler);
        }

        @Override
        public Document newDocument() {
            AccessibleDocumentBuilderFactory.NEW_DOCUMENT_CALLS.incrementAndGet();
            return delegate.newDocument();
        }

        @Override
        public DOMImplementation getDOMImplementation() {
            return delegate.getDOMImplementation();
        }
    }
}
