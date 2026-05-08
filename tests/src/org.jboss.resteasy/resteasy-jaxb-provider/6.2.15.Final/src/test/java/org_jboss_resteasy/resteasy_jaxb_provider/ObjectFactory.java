/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory {
    public JAXBElement<JAXBXmlTypeProviderTest.XmlTypeValue> createXmlTypeValue(
            JAXBXmlTypeProviderTest.XmlTypeValue value) {
        return new JAXBElement<>(JAXBXmlTypeProviderTest.XML_TYPE_VALUE_NAME,
                JAXBXmlTypeProviderTest.XmlTypeValue.class, null, value);
    }
}
