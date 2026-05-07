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
import java.util.Objects;

import org.jdom.JDOMException;
import org.jdom.adapters.AbstractDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;

public class AbstractDOMAdapterTest {
    @Test
    void setInternalSubsetInvokesParserSpecificDocumentTypeMutator() {
        TestDOMAdapter adapter = new TestDOMAdapter();
        MutableDocumentType documentType = new MutableDocumentType(
                "article", "-//example//DTD Article//EN", "article.dtd");

        adapter.applyInternalSubset(documentType, "<!ELEMENT article (#PCDATA)>");

        assertThat(documentType.getInternalSubset()).isEqualTo("<!ELEMENT article (#PCDATA)>");
    }

    public static class TestDOMAdapter extends AbstractDOMAdapter {
        public void applyInternalSubset(DocumentType documentType, String internalSubset) {
            setInternalSubset(documentType, internalSubset);
        }

        @Override
        public Document getDocument(InputStream inputStream, boolean validate) throws IOException, JDOMException {
            throw new UnsupportedOperationException("Parsing is not needed for this test");
        }

        @Override
        public Document createDocument() throws JDOMException {
            throw new UnsupportedOperationException("Document creation is not needed for this test");
        }
    }

    public static class MutableDocumentType implements DocumentType {
        private final String name;
        private final String publicId;
        private final String systemId;
        private String internalSubset;

        public MutableDocumentType(String name, String publicId, String systemId) {
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
            return EmptyNamedNodeMap.INSTANCE;
        }

        @Override
        public NamedNodeMap getNotations() {
            return EmptyNamedNodeMap.INSTANCE;
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
        public void setNodeValue(String nodeValue) {
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
        public Node insertBefore(Node newChild, Node refChild) {
            throw new UnsupportedOperationException("DocumentType nodes are immutable in this test");
        }

        @Override
        public Node replaceChild(Node newChild, Node oldChild) {
            throw new UnsupportedOperationException("DocumentType nodes are immutable in this test");
        }

        @Override
        public Node removeChild(Node oldChild) {
            throw new UnsupportedOperationException("DocumentType nodes are immutable in this test");
        }

        @Override
        public Node appendChild(Node newChild) {
            throw new UnsupportedOperationException("DocumentType nodes are immutable in this test");
        }

        @Override
        public boolean hasChildNodes() {
            return false;
        }

        @Override
        public Node cloneNode(boolean deep) {
            MutableDocumentType clone = new MutableDocumentType(name, publicId, systemId);
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
        public void setPrefix(String prefix) {
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
        public short compareDocumentPosition(Node other) {
            return 0;
        }

        @Override
        public String getTextContent() {
            return null;
        }

        @Override
        public void setTextContent(String textContent) {
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
            if (!(arg instanceof DocumentType)) {
                return false;
            }
            DocumentType other = (DocumentType) arg;
            return Objects.equals(name, other.getName())
                    && Objects.equals(publicId, other.getPublicId())
                    && Objects.equals(systemId, other.getSystemId())
                    && Objects.equals(internalSubset, other.getInternalSubset());
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

    public enum EmptyNodeList implements NodeList {
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

    public enum EmptyNamedNodeMap implements NamedNodeMap {
        INSTANCE;

        @Override
        public Node getNamedItem(String name) {
            return null;
        }

        @Override
        public Node setNamedItem(Node arg) {
            throw new UnsupportedOperationException("Named nodes are not needed for this test");
        }

        @Override
        public Node removeNamedItem(String name) {
            throw new UnsupportedOperationException("Named nodes are not needed for this test");
        }

        @Override
        public Node item(int index) {
            return null;
        }

        @Override
        public int getLength() {
            return 0;
        }

        @Override
        public Node getNamedItemNS(String namespaceURI, String localName) {
            return null;
        }

        @Override
        public Node setNamedItemNS(Node arg) {
            throw new UnsupportedOperationException("Named nodes are not needed for this test");
        }

        @Override
        public Node removeNamedItemNS(String namespaceURI, String localName) {
            throw new UnsupportedOperationException("Named nodes are not needed for this test");
        }
    }
}
