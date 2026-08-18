/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.SupportedMethodSetProperty;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;

import static org.assertj.core.api.Assertions.assertThat;

public class SupportedMethodSetPropertyTest {
    @Test
    void exposesAndSerializesSupportedMethodNames() throws ParserConfigurationException {
        String[] methods = {"VERSION-CONTROL", "CHECKOUT", "MERGE"};

        SupportedMethodSetProperty property = new SupportedMethodSetProperty(methods);

        assertThat(property.getName()).isEqualTo(DeltaVConstants.SUPPORTED_METHOD_SET);
        assertThat(property.getValue()).isSameAs(methods);
        assertThat(property.isInvisibleInAllprop()).isTrue();

        Element supportedMethodSet = property.toXml(newDocument());

        assertThat(DomUtil.matches(
                supportedMethodSet,
                DeltaVConstants.SUPPORTED_METHOD_SET.getName(),
                DeltaVConstants.SUPPORTED_METHOD_SET.getNamespace())).isTrue();
        assertThat(methodNames(supportedMethodSet)).containsExactly(methods);
    }

    private static String[] methodNames(Element supportedMethodSet) {
        NodeList methodNodes = supportedMethodSet.getElementsByTagNameNS(
                DeltaVConstants.NAMESPACE.getURI(),
                DeltaVConstants.XML_SUPPORTED_METHOD);
        String[] methods = new String[methodNodes.getLength()];
        for (int i = 0; i < methodNodes.getLength(); i++) {
            Element methodElement = (Element) methodNodes.item(i);
            methods[i] = DomUtil.getAttribute(methodElement, "name", DeltaVConstants.NAMESPACE);
        }
        return methods;
    }

    private static Document newDocument() throws ParserConfigurationException {
        return DomUtil.BUILDER_FACTORY.newDocumentBuilder().newDocument();
    }
}
