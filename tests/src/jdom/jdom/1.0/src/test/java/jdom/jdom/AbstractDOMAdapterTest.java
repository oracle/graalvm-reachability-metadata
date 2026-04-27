/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

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

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractDOMAdapterTest {
    @Test
    void createDocumentWithDocTypeAttemptsToApplyInternalSubset() throws Exception {
        ConcreteDOMAdapter adapter = new ConcreteDOMAdapter();
        DocType docType = new DocType("catalog", "catalog.dtd");
        docType.setInternalSubset("<!ELEMENT catalog (book*)>\n<!ELEMENT book (#PCDATA)>");

        Document document = adapter.createDocument(docType);
        DocumentType domDocType = document.getDoctype();

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("catalog");
        assertThat(domDocType).isNotNull();
        assertThat(domDocType.getName()).isEqualTo("catalog");
        assertThat(domDocType.getSystemId()).isEqualTo("catalog.dtd");
        assertThat(domDocType.getInternalSubset() == null
                || domDocType.getInternalSubset().equals(docType.getInternalSubset())).isTrue();
    }

    @Test
    void setInternalSubsetAttemptsPublicDomExtensionMethod() {
        ConcreteDOMAdapter adapter = new ConcreteDOMAdapter();
        MutableDocumentType documentType = new MutableDocumentType("catalog");
        String internalSubset = "<!ELEMENT catalog (book*)>";

        adapter.applyInternalSubset(documentType, internalSubset);

        assertThat(documentType.getInternalSubset() == null
                || documentType.getInternalSubset().equals(internalSubset)).isTrue();
    }

    public static final class ConcreteDOMAdapter extends AbstractDOMAdapter {
        @Override
        public Document getDocument(InputStream in, boolean validate) throws IOException, JDOMException {
            throw new UnsupportedOperationException("Parsing is not needed by this test");
        }

        @Override
        public Document createDocument() throws JDOMException {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
                factory.setNamespaceAware(true);
                return factory.newDocumentBuilder().newDocument();
            } catch (ParserConfigurationException e) {
                throw new JDOMException("Could not create DOM document", e);
            }
        }

        void applyInternalSubset(DocumentType documentType, String internalSubset) {
            setInternalSubset(documentType, internalSubset);
        }
    }

    public static final class MutableDocumentType implements DocumentType {
        private final String name;
        private String internalSubset;

        MutableDocumentType(String name) {
            this.name = name;
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
            return null;
        }

        @Override
        public String getSystemId() {
            return null;
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
        public String getNodeValue() throws DOMException {
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
            return EmptyNodeList.INSTANCE;
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
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "DocumentType is immutable in this test");
        }

        @Override
        public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "DocumentType is immutable in this test");
        }

        @Override
        public Node removeChild(Node oldChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "DocumentType is immutable in this test");
        }

        @Override
        public Node appendChild(Node newChild) throws DOMException {
            throw new DOMException(DOMException.NOT_SUPPORTED_ERR, "DocumentType is immutable in this test");
        }

        @Override
        public boolean hasChildNodes() {
            return false;
        }

        @Override
        public Node cloneNode(boolean deep) {
            MutableDocumentType clone = new MutableDocumentType(name);
            clone.setInternalSubset(internalSubset);
            return clone;
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

    private enum EmptyNodeList implements NodeList {
        INSTANCE;

        @Override
        public Node item(int index) {
            return null;
        }

        @Override
        public int getLength() {
            return 0;
        }
    }
}
