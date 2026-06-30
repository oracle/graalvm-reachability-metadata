/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.LabelSetProperty;
import org.apache.jackrabbit.webdav.version.VersionResource;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LabelSetPropertyTest {
    @Test
    void exposesAndSerializesLabelNames() throws Exception {
        String[] labels = {"release-1", "latest"};

        LabelSetProperty property = new LabelSetProperty(labels);

        assertThat(property.getName()).isEqualTo(VersionResource.LABEL_NAME_SET);
        assertThat(property.getValue()).isSameAs(labels);
        assertThat(property.isInvisibleInAllprop()).isTrue();

        Document document = newDocument();
        Element labelNameSet = property.toXml(document);

        assertThat(DomUtil.matches(
                labelNameSet,
                VersionResource.LABEL_NAME_SET.getName(),
                VersionResource.LABEL_NAME_SET.getNamespace())).isTrue();
        assertThat(labelTexts(labelNameSet)).containsExactly("release-1", "latest");
    }

    private static String[] labelTexts(Element labelNameSet) {
        NodeList labelNodes = labelNameSet.getElementsByTagNameNS(
                DeltaVConstants.NAMESPACE.getURI(),
                DeltaVConstants.XML_LABEL_NAME);
        String[] labels = new String[labelNodes.getLength()];
        for (int i = 0; i < labelNodes.getLength(); i++) {
            labels[i] = labelNodes.item(i).getTextContent();
        }
        return labels;
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
