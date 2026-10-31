/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import com_sun_xml_bind.jaxb_impl.model.Book;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JAXBContextImplTest {
    @Test
    void introspectsRootObjectsAndElementWrappers() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Book.class);
        JAXBIntrospector introspector = context.createJAXBIntrospector();
        Book book = Book.example();

        assertTrue(introspector.isElement(book));
        assertEquals(new QName(Book.NAMESPACE, "book"), introspector.getElementName(book));
        assertFalse(introspector.isElement("not an element"));
        assertNull(introspector.getElementName("not an element"));

        QName alternateName = new QName(Book.NAMESPACE, "featuredBook");
        JAXBElement<Book> wrapper = new JAXBElement<>(alternateName, Book.class, book);
        assertTrue(introspector.isElement(wrapper));
        assertEquals(alternateName, introspector.getElementName(wrapper));
        assertSame(book, JAXBIntrospector.getValue(wrapper));
    }

    @Test
    void generatesSchemaThatValidatesMarshalledDocuments() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Book.class);
        StringWriter schemaXml = new StringWriter();
        context.generateSchema(new SchemaOutputResolver() {
            @Override
            public Result createOutput(String namespaceUri, String suggestedFileName) {
                assertEquals(Book.NAMESPACE, namespaceUri);
                StreamResult output = new StreamResult(schemaXml);
                output.setSystemId("memory:/books.xsd");
                return output;
            }
        });

        assertTrue(schemaXml.toString().contains("bookType"));
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(schemaXml.toString())));
        StringWriter xml = new StringWriter();
        context.createMarshaller().marshal(Book.example(), xml);
        schema.newValidator().validate(new StreamSource(new StringReader(xml.toString())));
    }
}
