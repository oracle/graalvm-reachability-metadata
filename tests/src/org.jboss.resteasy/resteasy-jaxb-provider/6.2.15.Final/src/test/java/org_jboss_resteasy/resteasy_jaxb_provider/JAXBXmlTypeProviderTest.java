/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.namespace.QName;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlType;

import org.jboss.resteasy.plugins.providers.jaxb.JAXBXmlTypeProvider;
import org.junit.jupiter.api.Test;

public class JAXBXmlTypeProviderTest {
    static final QName XML_TYPE_VALUE_NAME = new QName("urn:resteasy-jaxb-provider-test", "xmlTypeValue");

    @Test
    void wrapsXmlTypeValueWithDefaultObjectFactoryCreateMethod() {
        XmlTypeValue value = new XmlTypeValue("native-image");

        JAXBElement<?> element = JAXBXmlTypeProvider.wrapInJAXBElement(value, XmlTypeValue.class);

        assertThat(element.getValue()).isSameAs(value);
        assertThat(element.getDeclaredType()).isEqualTo(XmlTypeValue.class);
        assertThat(element.getName()).isEqualTo(XML_TYPE_VALUE_NAME);
    }

    @XmlType(name = "xmlTypeValue")
    public static final class XmlTypeValue {
        private final String name;

        XmlTypeValue(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
