/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TxLockEntry;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class TxLockEntryTest {
    @Test
    public void createsLocalAndGlobalTransactionLockEntries() throws Exception {
        TxLockEntry localLockEntry = new TxLockEntry(true);
        TxLockEntry globalLockEntry = new TxLockEntry(false);

        assertThat(localLockEntry.getType()).isEqualTo(TransactionConstants.TRANSACTION);
        assertThat(localLockEntry.getScope()).isEqualTo(TransactionConstants.LOCAL);
        assertThat(globalLockEntry.getType()).isEqualTo(TransactionConstants.TRANSACTION);
        assertThat(globalLockEntry.getScope()).isEqualTo(TransactionConstants.GLOBAL);

        assertTransactionLockEntryXml(localLockEntry.toXml(newDocument()), TransactionConstants.XML_LOCAL);
        assertTransactionLockEntryXml(globalLockEntry.toXml(newDocument()), TransactionConstants.XML_GLOBAL);
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static void assertTransactionLockEntryXml(Element lockEntryElement, String scopeLocalName) {
        assertThat(lockEntryElement.getLocalName()).isEqualTo(DavConstants.XML_LOCKENTRY);
        assertThat(lockEntryElement.getNamespaceURI()).isEqualTo(DavConstants.NAMESPACE.getURI());
        assertThat(lockEntryElement.getElementsByTagNameNS(
                DavConstants.NAMESPACE.getURI(),
                DavConstants.XML_LOCKSCOPE).getLength())
                .isEqualTo(1);
        assertThat(lockEntryElement.getElementsByTagNameNS(
                TransactionConstants.NAMESPACE.getURI(),
                scopeLocalName).getLength())
                .isEqualTo(1);
        assertThat(lockEntryElement.getElementsByTagNameNS(
                DavConstants.NAMESPACE.getURI(),
                DavConstants.XML_LOCKTYPE).getLength())
                .isEqualTo(1);
        assertThat(lockEntryElement.getElementsByTagNameNS(
                TransactionConstants.NAMESPACE.getURI(),
                TransactionConstants.XML_TRANSACTION).getLength())
                .isEqualTo(1);
    }
}
