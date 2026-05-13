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

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.OptionsInfo;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OptionsInfoTest {
    @Test
    public void createFromXmlReadsDeltaVOptionElementsAndSerializesThem() throws Exception {
        Document requestDocument = parseXml("""
                <D:options xmlns:D="DAV:">
                    <D:version-history-collection-set/>
                    <D:workspace-collection-set/>
                </D:options>
                """);

        OptionsInfo optionsInfo = OptionsInfo.createFromXml(requestDocument.getDocumentElement());

        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_WSP_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_ACTIVITY_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isFalse();
        assertThat(optionsInfo.containsElement(
                        DeltaVConstants.XML_VH_COLLECTION_SET,
                        Namespace.getNamespace("other", "urn:other")))
                .isFalse();

        Element optionsElement = optionsInfo.toXml(newDocument());

        assertThat(optionsElement.getLocalName()).isEqualTo(DeltaVConstants.XML_OPTIONS);
        assertThat(optionsElement.getNamespaceURI()).isEqualTo(DeltaVConstants.NAMESPACE.getURI());
        assertThat(hasDeltaVChild(optionsElement, DeltaVConstants.XML_VH_COLLECTION_SET)).isTrue();
        assertThat(hasDeltaVChild(optionsElement, DeltaVConstants.XML_WSP_COLLECTION_SET)).isTrue();
    }

    @Test
    public void constructorStoresRequestedOptionNamesForSerialization() throws Exception {
        OptionsInfo optionsInfo = new OptionsInfo(new String[] {
            DeltaVConstants.XML_ACTIVITY_COLLECTION_SET,
            DeltaVConstants.XML_WSP_COLLECTION_SET
        });

        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_ACTIVITY_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isTrue();
        assertThat(optionsInfo.containsElement(DeltaVConstants.XML_VH_COLLECTION_SET, DeltaVConstants.NAMESPACE))
                .isFalse();

        Element optionsElement = optionsInfo.toXml(newDocument());

        assertThat(hasDeltaVChild(optionsElement, DeltaVConstants.XML_ACTIVITY_COLLECTION_SET)).isTrue();
        assertThat(hasDeltaVChild(optionsElement, DeltaVConstants.XML_WSP_COLLECTION_SET)).isTrue();
        assertThat(hasDeltaVChild(optionsElement, DeltaVConstants.XML_VH_COLLECTION_SET)).isFalse();
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

    private static boolean hasDeltaVChild(Element parent, String localName) {
        return parent.getElementsByTagNameNS(DeltaVConstants.NAMESPACE.getURI(), localName).getLength() > 0;
    }
}
