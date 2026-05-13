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

import org.apache.jackrabbit.webdav.bind.BindConstants;
import org.apache.jackrabbit.webdav.bind.RebindInfo;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class RebindInfoTest {
    @Test
    public void createFromXmlReadsHrefAndSegmentAndRoundTripsToXml() throws Exception {
        Document requestDocument = parseXml("""
                <D:rebind xmlns:D="DAV:">
                    <D:href>/documents/source.txt</D:href>
                    <D:segment>linked-source.txt</D:segment>
                </D:rebind>
                """);

        RebindInfo rebindInfo = RebindInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(rebindInfo.getHref()).isEqualTo("/documents/source.txt");
        assertThat(rebindInfo.getSegment()).isEqualTo("linked-source.txt");

        Element rebindElement = rebindInfo.toXml(newDocument());

        assertThat(rebindElement.getLocalName()).isEqualTo(BindConstants.XML_REBIND);
        assertThat(rebindElement.getNamespaceURI()).isEqualTo(BindConstants.NAMESPACE.getURI());
        assertThat(textOfFirstChild(rebindElement, BindConstants.XML_HREF)).isEqualTo("/documents/source.txt");
        assertThat(textOfFirstChild(rebindElement, BindConstants.XML_SEGMENT)).isEqualTo("linked-source.txt");
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

    private static String textOfFirstChild(Element parent, String localName) {
        return parent.getElementsByTagNameNS(BindConstants.NAMESPACE.getURI(), localName)
                .item(0)
                .getTextContent();
    }
}
