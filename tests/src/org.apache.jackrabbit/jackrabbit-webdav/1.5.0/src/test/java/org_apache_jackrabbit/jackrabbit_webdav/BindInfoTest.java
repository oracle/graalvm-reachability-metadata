/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.bind.BindConstants;
import org.apache.jackrabbit.webdav.bind.BindInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class BindInfoTest {
    @Test
    void parsesBindRequestXmlAndSerializesItBackToWebdavXml() throws Exception {
        Document requestDocument = parse("""
                <D:bind xmlns:D="DAV:">
                    <D:href>/repository/source-node</D:href>
                    <D:segment>bound-node</D:segment>
                </D:bind>
                """);

        BindInfo bindInfo = BindInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(bindInfo.getHref()).isEqualTo("/repository/source-node");
        assertThat(bindInfo.getSegment()).isEqualTo("bound-node");

        Document responseDocument = newDocument();
        Element bindElement = bindInfo.toXml(responseDocument);

        assertThat(DomUtil.matches(bindElement, BindConstants.XML_BIND, BindConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getChildText(bindElement, BindConstants.XML_HREF, BindConstants.NAMESPACE))
                .isEqualTo("/repository/source-node");
        assertThat(DomUtil.getChildText(bindElement, BindConstants.XML_SEGMENT, BindConstants.NAMESPACE))
                .isEqualTo("bound-node");
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
