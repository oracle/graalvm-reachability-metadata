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
import org.apache.jackrabbit.webdav.bind.ParentElement;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class ParentElementTest {
    @Test
    public void createFromXmlReadsParentHrefAndSegment() throws Exception {
        Document document = parseXml("""
                <D:parent xmlns:D="DAV:">
                    <D:href>/collections/projects/</D:href>
                    <D:segment>readme.txt</D:segment>
                </D:parent>
                """);

        ParentElement parentElement = ParentElement.createFromXml(document.getDocumentElement());

        assertThat(parentElement.getHref()).isEqualTo("/collections/projects/");
        assertThat(parentElement.getSegment()).isEqualTo("readme.txt");
    }

    @Test
    public void toXmlWritesDavParentWithHrefAndSegment() throws Exception {
        ParentElement parentElement = new ParentElement("/workspace/", "draft.doc");

        Element parent = parentElement.toXml(newDocument());

        assertThat(parent.getLocalName()).isEqualTo(BindConstants.XML_PARENT);
        assertThat(parent.getNamespaceURI()).isEqualTo(BindConstants.NAMESPACE.getURI());
        assertThat(textOfFirstChild(parent, BindConstants.XML_HREF)).isEqualTo("/workspace/");
        assertThat(textOfFirstChild(parent, BindConstants.XML_SEGMENT)).isEqualTo("draft.doc");
    }

    private static Document parseXml(String xml) throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        return newDocumentBuilderFactory().newDocumentBuilder().parse(input);
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
