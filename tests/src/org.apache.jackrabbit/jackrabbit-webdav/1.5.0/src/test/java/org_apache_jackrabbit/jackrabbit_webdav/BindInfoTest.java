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
import org.apache.jackrabbit.webdav.bind.BindInfo;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class BindInfoTest {
    @Test
    public void createFromXmlReadsHrefAndSegmentAndRoundTripsToXml() throws Exception {
        Document requestDocument = parseXml("""
                <D:bind xmlns:D="DAV:">
                    <D:href>/documents/source.txt</D:href>
                    <D:segment>linked-source.txt</D:segment>
                </D:bind>
                """);

        BindInfo bindInfo = BindInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(bindInfo.getHref()).isEqualTo("/documents/source.txt");
        assertThat(bindInfo.getSegment()).isEqualTo("linked-source.txt");

        Element bindElement = bindInfo.toXml(newDocument());

        assertThat(bindElement.getLocalName()).isEqualTo(BindConstants.XML_BIND);
        assertThat(bindElement.getNamespaceURI()).isEqualTo(BindConstants.NAMESPACE.getURI());
        assertThat(textOfFirstChild(bindElement, BindConstants.XML_HREF)).isEqualTo("/documents/source.txt");
        assertThat(textOfFirstChild(bindElement, BindConstants.XML_SEGMENT)).isEqualTo("linked-source.txt");
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
