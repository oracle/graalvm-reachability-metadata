/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.lock.AbstractLockEntry;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLockEntryTest {
    @Test
    void serializesLockScopeAndTypeAsLockEntry() throws Exception {
        AbstractLockEntry lockEntry = new FixedLockEntry(Scope.EXCLUSIVE, Type.WRITE);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element element = lockEntry.toXml(document);

        assertThat(DomUtil.matches(element, DavConstants.XML_LOCKENTRY, DavConstants.NAMESPACE)).isTrue();
        Element scopeElement = DomUtil.getChildElement(element, DavConstants.XML_LOCKSCOPE, DavConstants.NAMESPACE);
        assertThat(scopeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(scopeElement, DavConstants.XML_EXCLUSIVE, DavConstants.NAMESPACE)).isTrue();
        Element typeElement = DomUtil.getChildElement(element, DavConstants.XML_LOCKTYPE, DavConstants.NAMESPACE);
        assertThat(typeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(typeElement, DavConstants.XML_WRITE, DavConstants.NAMESPACE)).isTrue();
    }

    private static final class FixedLockEntry extends AbstractLockEntry {
        private final Scope scope;
        private final Type type;

        private FixedLockEntry(Scope scope, Type type) {
            this.scope = scope;
            this.type = type;
        }

        @Override
        public Scope getScope() {
            return scope;
        }

        @Override
        public Type getType() {
            return type;
        }
    }
}
