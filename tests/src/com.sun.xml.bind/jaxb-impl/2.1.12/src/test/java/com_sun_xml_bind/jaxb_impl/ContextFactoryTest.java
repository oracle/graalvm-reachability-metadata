/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;

import com_sun_xml_bind.jaxb_impl.model.Book;
import com_sun_xml_bind.jaxb_impl.registry.ObjectFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ContextFactoryTest {
    @Test
    void discoversBoundClassesFromJaxbIndex() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Book.class.getPackageName());
        StringWriter xml = new StringWriter();
        context.createMarshaller().marshal(Book.example(), xml);

        Book result = assertInstanceOf(Book.class, context.createUnmarshaller().unmarshal(new StringReader(xml.toString())));
        assertEquals("guide", result.getId());
        assertEquals("Native & Java", result.getTitle());
        assertEquals("9781234567890", result.getIsbn().getDigits());
    }

    @Test
    void discoversElementDeclarationsFromObjectFactory() throws Exception {
        JAXBContext context = JAXBContext.newInstance(ObjectFactory.class.getPackageName());
        JAXBElement<String> message = new ObjectFactory().createMessage("Hello & goodbye");
        StringWriter xml = new StringWriter();
        context.createMarshaller().marshal(message, xml);

        JAXBElement<?> result = assertInstanceOf(JAXBElement.class, context.createUnmarshaller().unmarshal(new StringReader(xml.toString())));
        assertEquals(new QName("urn:forge:messages", "message"), result.getName());
        assertEquals(String.class, result.getDeclaredType());
        assertEquals("Hello & goodbye", result.getValue());
    }

    @Test
    void rejectsUnrecognizedContextProperties() {
        assertThrows(JAXBException.class, () -> JAXBContext.newInstance(new Class<?>[]{Book.class}, Map.of("unknown.setting", true)));
    }
}
