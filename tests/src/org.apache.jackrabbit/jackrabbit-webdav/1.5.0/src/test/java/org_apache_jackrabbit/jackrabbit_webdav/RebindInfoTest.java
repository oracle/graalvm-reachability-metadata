/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.bind.BindConstants;
import org.apache.jackrabbit.webdav.bind.RebindInfo;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class RebindInfoTest {
    @Test
    void parsesRebindRequestXmlAndSerializesItBackToWebdavXml() throws Exception {
        Document requestDocument = parse("""
                <D:rebind xmlns:D="DAV:">
                    <D:href>/repository/existing-node</D:href>
                    <D:segment>new-binding-name</D:segment>
                </D:rebind>
                """);

        RebindInfo rebindInfo = RebindInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(rebindInfo.getHref()).isEqualTo("/repository/existing-node");
        assertThat(rebindInfo.getSegment()).isEqualTo("new-binding-name");

        Document responseDocument = newDocument();
        Element rebindElement = rebindInfo.toXml(responseDocument);

        assertThat(DomUtil.matches(rebindElement, BindConstants.XML_REBIND, BindConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getChildText(rebindElement, BindConstants.XML_HREF, BindConstants.NAMESPACE))
                .isEqualTo("/repository/existing-node");
        assertThat(DomUtil.getChildText(rebindElement, BindConstants.XML_SEGMENT, BindConstants.NAMESPACE))
                .isEqualTo("new-binding-name");
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
