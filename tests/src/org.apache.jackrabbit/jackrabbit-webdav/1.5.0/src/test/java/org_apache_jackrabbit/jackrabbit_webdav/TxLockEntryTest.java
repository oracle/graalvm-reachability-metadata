/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TxLockEntry;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TxLockEntryTest {
    @Test
    void createsLocalTransactionLockEntry() throws Exception {
        TxLockEntry lockEntry = new TxLockEntry(true);

        assertThat(lockEntry.getType()).isEqualTo(TransactionConstants.TRANSACTION);
        assertThat(lockEntry.getScope()).isEqualTo(TransactionConstants.LOCAL);
        assertLockEntryXml(lockEntry.toXml(newDocument()), TransactionConstants.XML_LOCAL);
    }

    @Test
    void createsGlobalTransactionLockEntry() throws Exception {
        TxLockEntry lockEntry = new TxLockEntry(false);

        assertThat(lockEntry.getType()).isEqualTo(TransactionConstants.TRANSACTION);
        assertThat(lockEntry.getScope()).isEqualTo(TransactionConstants.GLOBAL);
        assertLockEntryXml(lockEntry.toXml(newDocument()), TransactionConstants.XML_GLOBAL);
    }

    private static void assertLockEntryXml(Element lockEntryElement, String scopeElementName) {
        assertThat(DomUtil.matches(lockEntryElement, DavConstants.XML_LOCKENTRY, DavConstants.NAMESPACE)).isTrue();

        Element lockTypeElement = DomUtil.getChildElement(
                lockEntryElement,
                DavConstants.XML_LOCKTYPE,
                DavConstants.NAMESPACE);
        assertThat(lockTypeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                lockTypeElement,
                TransactionConstants.XML_TRANSACTION,
                TransactionConstants.NAMESPACE)).isTrue();

        Element lockScopeElement = DomUtil.getChildElement(
                lockEntryElement,
                DavConstants.XML_LOCKSCOPE,
                DavConstants.NAMESPACE);
        assertThat(lockScopeElement).isNotNull();
        assertThat(DomUtil.hasChildElement(
                lockScopeElement,
                scopeElementName,
                TransactionConstants.NAMESPACE)).isTrue();
    }

    private static Document newDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }
}
