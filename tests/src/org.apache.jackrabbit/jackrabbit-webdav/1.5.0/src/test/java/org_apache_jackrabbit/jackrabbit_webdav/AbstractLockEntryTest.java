/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.lock.AbstractLockEntry;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AbstractLockEntryTest {
    @Test
    public void toXmlSerializesScopeAndTypeElements() throws Exception {
        TestLockEntry lockEntry = new TestLockEntry(Scope.EXCLUSIVE, Type.WRITE);
        Element lockEntryElement = lockEntry.toXml(newDocument());

        assertThat(lockEntryElement.getLocalName()).isEqualTo(DavConstants.XML_LOCKENTRY);
        assertThat(lockEntryElement.getNamespaceURI()).isEqualTo(DavConstants.NAMESPACE.getURI());
        assertThat(childLocalNames(lockEntryElement)).containsExactly(
                DavConstants.XML_LOCKSCOPE,
                DavConstants.XML_LOCKTYPE);

        Element lockScopeElement = firstChildElement(lockEntryElement, DavConstants.XML_LOCKSCOPE);
        Element lockTypeElement = firstChildElement(lockEntryElement, DavConstants.XML_LOCKTYPE);
        assertThat(childLocalNames(lockScopeElement)).containsExactly(DavConstants.XML_EXCLUSIVE);
        assertThat(childLocalNames(lockTypeElement)).containsExactly(DavConstants.XML_WRITE);
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static Element firstChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        throw new AssertionError("Missing child element: " + localName);
    }

    private static List<String> childLocalNames(Element parent) {
        List<String> names = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                names.add(child.getLocalName());
            }
        }
        return names;
    }

    private static final class TestLockEntry extends AbstractLockEntry {
        private final Scope scope;
        private final Type type;

        private TestLockEntry(Scope scope, Type type) {
            this.scope = scope;
            this.type = type;
        }

        public Scope getScope() {
            return scope;
        }

        public Type getType() {
            return type;
        }
    }
}
