/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl.registry;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

@XmlRegistry
public class ObjectFactory {
    @XmlElementDecl(namespace = "urn:forge:messages", name = "message")
    public JAXBElement<String> createMessage(String value) {
        return new JAXBElement<>(new QName("urn:forge:messages", "message"), String.class, value);
    }
}
