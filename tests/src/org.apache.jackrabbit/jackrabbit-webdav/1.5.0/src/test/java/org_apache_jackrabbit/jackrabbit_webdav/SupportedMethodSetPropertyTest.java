/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.SupportedMethodSetProperty;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SupportedMethodSetPropertyTest {
    @Test
    public void constructorStoresSupportedMethodsAndToXmlWritesMethodElements() throws Exception {
        String[] methods = new String[] {
            DavMethods.METHOD_CHECKIN,
            DavMethods.METHOD_CHECKOUT,
            DavMethods.METHOD_VERSION_CONTROL
        };

        SupportedMethodSetProperty property = new SupportedMethodSetProperty(methods);
        Element element = property.toXml(newDocument());

        assertThat(property.getName()).isEqualTo(DeltaVConstants.SUPPORTED_METHOD_SET);
        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat((String[]) property.getValue()).containsExactly(methods);
        assertThat(element.getLocalName()).isEqualTo("supported-method-set");
        assertThat(element.getNamespaceURI()).isEqualTo(DeltaVConstants.NAMESPACE.getURI());
        assertThat(supportedMethodNames(element)).containsExactly(methods);
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static String[] supportedMethodNames(Element element) {
        NodeList methodElements = element.getElementsByTagNameNS(
                DeltaVConstants.NAMESPACE.getURI(), DeltaVConstants.XML_SUPPORTED_METHOD);
        String[] methodNames = new String[methodElements.getLength()];
        for (int i = 0; i < methodElements.getLength(); i++) {
            Element methodElement = (Element) methodElements.item(i);
            methodNames[i] = methodElement.getAttributeNS(DeltaVConstants.NAMESPACE.getURI(), "name");
        }
        return methodNames;
    }
}
