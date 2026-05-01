/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jdom.DocType;
import org.jdom.JDOMException;
import org.jdom.adapters.AbstractDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

public class AbstractDOMAdapterTest {
    @Test
    void setInternalSubsetInvokesAccessibleExtensionSetter() {
        ExposingDocumentAdapter adapter = new ExposingDocumentAdapter();
        MutableDocumentType documentType = new MutableDocumentType("book", "-//JDOM Test//DTD Book//EN", "book.dtd");
        String internalSubset = """
                <!ELEMENT book (title)>
                <!ELEMENT title (#PCDATA)>
                """.trim();

        adapter.applyInternalSubset(documentType, internalSubset);

        assertThat(documentType.getInternalSubset()).isEqualTo(internalSubset);
    }

    @Test
    void createDocumentWithDocTypeAttemptsToApplyInternalSubset() throws Exception {
        AbstractDOMAdapter adapter = new StandardDocumentAdapter();
        String internalSubset = """
                <!ELEMENT book (title)>
                <!ELEMENT title (#PCDATA)>
                """.trim();
        DocType docType = new DocType("book", "-//JDOM Test//DTD Book//EN", "book.dtd");
        docType.setInternalSubset(internalSubset);

        Document document = adapter.createDocument(docType);

        DocumentType documentType = document.getDoctype();
        assertThat(documentType).isNotNull();
        assertThat(documentType.getName()).isEqualTo("book");
        assertThat(documentType.getPublicId()).isEqualTo("-//JDOM Test//DTD Book//EN");
        assertThat(documentType.getSystemId()).isEqualTo("book.dtd");
        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("http://temporary");
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("book");
    }

    private static final class ExposingDocumentAdapter extends StandardDocumentAdapter {
        void applyInternalSubset(DocumentType documentType, String internalSubset) {
            setInternalSubset(documentType, internalSubset);
        }
    }

    private static class StandardDocumentAdapter extends AbstractDOMAdapter {
        @Override
        public Document getDocument(InputStream in, boolean validate) throws IOException, JDOMException {
            throw new UnsupportedOperationException("Parsing is not needed for this AbstractDOMAdapter coverage test");
        }

        @Override
        public Document createDocument() throws JDOMException {
            try {
                return DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                throw new JDOMException("Unable to create a DOM document", e);
            }
        }
    }

    public static final class MutableDocumentType implements DocumentType {
        private final String name;
        private final String publicId;
        private final String systemId;
        private String internalSubset;

        MutableDocumentType(String name, String publicId, String systemId) {
            this.name = name;
            this.publicId = publicId;
            this.systemId = systemId;
        }

        public void setInternalSubset(String internalSubset) {
            this.internalSubset = internalSubset;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public NamedNodeMap getEntities() {
            return null;
        }

        @Override
        public NamedNodeMap getNotations() {
            return null;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public String getInternalSubset() {
            return internalSubset;
        }

        @Override
        public String getNodeName() {
            return name;
        }

        @Override
        public String getNodeValue() {
            return null;
        }

        @Override
        public void setNodeValue(String nodeValue) throws DOMException {
        }

        @Override
        public short getNodeType() {
            return Node.DOCUMENT_TYPE_NODE;
        }

        @Override
        public Node getParentNode() {
            return null;
        }

        @Override
        public NodeList getChildNodes() {
            return null;
        }

        @Override
        public Node getFirstChild() {
            return null;
        }

        @Override
        public Node getLastChild() {
            return null;
        }

        @Override
        public Node getPreviousSibling() {
            return null;
        }

        @Override
        public Node getNextSibling() {
            return null;
        }

        @Override
        public NamedNodeMap getAttributes() {
            return null;
        }

        @Override
        public Document getOwnerDocument() {
            return null;
        }

        @Override
        public Node insertBefore(Node newChild, Node refChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "This test doctype is immutable");
        }

        @Override
        public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "This test doctype is immutable");
        }

        @Override
        public Node removeChild(Node oldChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "This test doctype is immutable");
        }

        @Override
        public Node appendChild(Node newChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "This test doctype is immutable");
        }

        @Override
        public boolean hasChildNodes() {
            return false;
        }

        @Override
        public Node cloneNode(boolean deep) {
            return new MutableDocumentType(name, publicId, systemId);
        }

        @Override
        public void normalize() {
        }

        @Override
        public boolean isSupported(String feature, String version) {
            return false;
        }

        @Override
        public String getNamespaceURI() {
            return null;
        }

        @Override
        public String getPrefix() {
            return null;
        }

        @Override
        public void setPrefix(String prefix) throws DOMException {
        }

        @Override
        public String getLocalName() {
            return name;
        }

        @Override
        public boolean hasAttributes() {
            return false;
        }

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public short compareDocumentPosition(Node other) throws DOMException {
            return 0;
        }

        @Override
        public String getTextContent() throws DOMException {
            return null;
        }

        @Override
        public void setTextContent(String textContent) throws DOMException {
        }

        @Override
        public boolean isSameNode(Node other) {
            return this == other;
        }

        @Override
        public String lookupPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public boolean isDefaultNamespace(String namespaceURI) {
            return false;
        }

        @Override
        public String lookupNamespaceURI(String prefix) {
            return null;
        }

        @Override
        public boolean isEqualNode(Node arg) {
            return this == arg;
        }

        @Override
        public Object getFeature(String feature, String version) {
            return null;
        }

        @Override
        public Object setUserData(String key, Object data, UserDataHandler handler) {
            return null;
        }

        @Override
        public Object getUserData(String key) {
            return null;
        }
    }
}
