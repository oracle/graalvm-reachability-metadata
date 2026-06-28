/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.bind.BindConstants;
import org.apache.jackrabbit.webdav.bind.ParentElement;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ParentElementTest {
    @Test
    void parsesParentXmlAndSerializesItBackToWebdavXml() throws Exception {
        Document requestDocument = parse("""
                <D:parent xmlns:D="DAV:">
                    <D:href>/repository/parent-node</D:href>
                    <D:segment>child-node</D:segment>
                </D:parent>
                """);

        ParentElement parentElement = ParentElement.createFromXml(requestDocument.getDocumentElement());

        assertThat(parentElement.getHref()).isEqualTo("/repository/parent-node");
        assertThat(parentElement.getSegment()).isEqualTo("child-node");

        Document responseDocument = newDocument();
        Element parentXml = parentElement.toXml(responseDocument);

        assertThat(DomUtil.matches(parentXml, BindConstants.XML_PARENT, BindConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getChildText(parentXml, BindConstants.XML_HREF, BindConstants.NAMESPACE))
                .isEqualTo("/repository/parent-node");
        assertThat(DomUtil.getChildText(parentXml, BindConstants.XML_SEGMENT, BindConstants.NAMESPACE))
                .isEqualTo("child-node");
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
