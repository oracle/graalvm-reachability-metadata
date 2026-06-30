/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.transaction.TransactionConstants;
import org.apache.jackrabbit.webdav.transaction.TransactionInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransactionInfoTest {
    @Test
    void parsesCommitTransactionInfoAndSerializesItBackToXml() throws Exception {
        Document requestDocument = parse("""
                <dcr:transactioninfo xmlns:dcr="http://www.day.com/jcr/webdav/1.0">
                    <dcr:transactionstatus>
                        <dcr:commit/>
                    </dcr:transactionstatus>
                </dcr:transactioninfo>
                """);

        TransactionInfo transactionInfo = new TransactionInfo(requestDocument.getDocumentElement());

        assertThat(transactionInfo.isCommit()).isTrue();

        Document responseDocument = newDocument();
        Element transactionInfoElement = transactionInfo.toXml(responseDocument);
        Element transactionStatusElement = DomUtil.getChildElement(
                transactionInfoElement,
                TransactionConstants.XML_TRANSACTIONSTATUS,
                TransactionConstants.NAMESPACE);

        assertThat(DomUtil.matches(
                transactionInfoElement,
                TransactionConstants.XML_TRANSACTIONINFO,
                TransactionConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.hasChildElement(
                transactionStatusElement,
                TransactionConstants.XML_COMMIT,
                TransactionConstants.NAMESPACE)).isTrue();
    }

    @Test
    void serializesRollbackTransactionInfo() throws Exception {
        TransactionInfo transactionInfo = new TransactionInfo(false);

        Document responseDocument = newDocument();
        Element transactionInfoElement = transactionInfo.toXml(responseDocument);
        Element transactionStatusElement = DomUtil.getChildElement(
                transactionInfoElement,
                TransactionConstants.XML_TRANSACTIONSTATUS,
                TransactionConstants.NAMESPACE);

        assertThat(transactionInfo.isCommit()).isFalse();
        assertThat(DomUtil.hasChildElement(
                transactionStatusElement,
                TransactionConstants.XML_ROLLBACK,
                TransactionConstants.NAMESPACE)).isTrue();
    }

    @Test
    void rejectsElementsWithoutTransactionStatus() throws Exception {
        Document requestDocument = parse("""
                <dcr:transactioninfo xmlns:dcr="http://www.day.com/jcr/webdav/1.0"/>
                """);

        assertThatThrownBy(() -> new TransactionInfo(requestDocument.getDocumentElement()))
                .isInstanceOfSatisfying(DavException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(DavServletResponse.SC_BAD_REQUEST));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilder builder = newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Document newDocument() throws Exception {
        DocumentBuilder builder = newDocumentBuilder();
        return builder.newDocument();
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
