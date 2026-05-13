/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class TransactionInfoTest {
    @Test
    public void parsesCommitTransactionInfoAndSerializesStatusElements() throws Exception {
        Document requestDocument = parseXml("""
                <dcr:transactioninfo xmlns:dcr="http://www.day.com/jcr/webdav/1.0">
                    <dcr:transactionstatus>
                        <dcr:commit/>
                    </dcr:transactionstatus>
                </dcr:transactioninfo>
                """);

        TransactionInfo commitInfo = new TransactionInfo(requestDocument.getDocumentElement());

        assertThat(commitInfo.isCommit()).isTrue();
        Element commitElement = commitInfo.toXml(newDocument());
        assertThat(commitElement.getLocalName()).isEqualTo(TransactionConstants.XML_TRANSACTIONINFO);
        assertThat(commitElement.getNamespaceURI()).isEqualTo(TransactionConstants.NAMESPACE.getURI());
        assertThat(hasChildElement(commitElement, TransactionConstants.XML_TRANSACTIONSTATUS)).isTrue();
        assertThat(hasChildElement(commitElement, TransactionConstants.XML_COMMIT)).isTrue();
        assertThat(hasChildElement(commitElement, TransactionConstants.XML_ROLLBACK)).isFalse();

        TransactionInfo rollbackInfo = new TransactionInfo(false);

        assertThat(rollbackInfo.isCommit()).isFalse();
        Element rollbackElement = rollbackInfo.toXml(newDocument());
        assertThat(hasChildElement(rollbackElement, TransactionConstants.XML_COMMIT)).isFalse();
        assertThat(hasChildElement(rollbackElement, TransactionConstants.XML_ROLLBACK)).isTrue();
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = newDocumentBuilderFactory();
        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        return factory.newDocumentBuilder().parse(input);
    }

    private static Document newDocument() throws Exception {
        return newDocumentBuilderFactory().newDocumentBuilder().newDocument();
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    private static boolean hasChildElement(Element parent, String localName) {
        return parent.getElementsByTagNameNS(TransactionConstants.NAMESPACE.getURI(), localName).getLength() > 0;
    }
}
