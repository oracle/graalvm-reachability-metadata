/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.LabelSetProperty;
import org.apache.jackrabbit.webdav.version.VersionResource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class LabelSetPropertyTest {
    @Test
    public void constructorStoresLabelsAndToXmlWritesLabelNameSet() throws Exception {
        String[] labels = new String[] {"release-1", "stable"};

        LabelSetProperty property = new LabelSetProperty(labels);
        Element element = property.toXml(newDocument());

        assertThat(property.getName()).isEqualTo(VersionResource.LABEL_NAME_SET);
        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat((String[]) property.getValue()).containsExactly(labels);
        assertThat(element.getLocalName()).isEqualTo("label-name-set");
        assertThat(element.getNamespaceURI()).isEqualTo(DeltaVConstants.NAMESPACE.getURI());
        assertThat(labelNameTexts(element)).containsExactly(labels);
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static String[] labelNameTexts(Element element) {
        NodeList labelNameElements = element.getElementsByTagNameNS(
                DeltaVConstants.NAMESPACE.getURI(), DeltaVConstants.XML_LABEL_NAME);
        String[] labelNames = new String[labelNameElements.getLength()];
        for (int i = 0; i < labelNameElements.getLength(); i++) {
            labelNames[i] = labelNameElements.item(i).getTextContent();
        }
        return labelNames;
    }
}
