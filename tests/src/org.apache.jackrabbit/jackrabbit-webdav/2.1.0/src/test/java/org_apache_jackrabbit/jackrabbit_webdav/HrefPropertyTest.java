/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.HrefProperty;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;

public class HrefPropertyTest {
    @Test
    void storesMultipleHrefValuesAndSerializesThemAsDavHrefElements() throws ParserConfigurationException {
        String[] hrefs = {"/documents/readme.txt", "/documents/archive.zip"};
        HrefProperty property = new HrefProperty(DavPropertyName.SOURCE, hrefs, true);

        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat((String[]) property.getValue()).containsExactly(hrefs);
        assertThat(property.getHrefs()).containsExactly(hrefs);

        Element propertyElement = property.toXml(newDocument());

        assertThat(propertyElement.getLocalName()).isEqualTo(DavPropertyName.SOURCE.getName());
        NodeList hrefElements = propertyElement.getElementsByTagNameNS(
                DavConstants.NAMESPACE.getURI(),
                DavConstants.XML_HREF);
        assertThat(hrefElements.getLength()).isEqualTo(2);
        assertThat(DomUtil.getText((Element) hrefElements.item(0)))
                .isEqualTo("/documents/readme.txt");
        assertThat(DomUtil.getText((Element) hrefElements.item(1)))
                .isEqualTo("/documents/archive.zip");
    }

    @Test
    void parsesHrefElementsFromXmlBackedProperty() throws ParserConfigurationException {
        Document document = newDocument();
        Element source = DavPropertyName.SOURCE.toXml(document);
        source.appendChild(DomUtil.hrefToXml("/workspace/a", document));
        source.appendChild(DomUtil.hrefToXml("/workspace/b", document));

        HrefProperty property = new HrefProperty(DefaultDavProperty.createFromXml(source));

        assertThat(property.getName()).isEqualTo(DavPropertyName.SOURCE);
        assertThat(property.getHrefs()).containsExactly("/workspace/a", "/workspace/b");
    }

    private static Document newDocument() throws ParserConfigurationException {
        return DomUtil.BUILDER_FACTORY.newDocumentBuilder().newDocument();
    }
}
