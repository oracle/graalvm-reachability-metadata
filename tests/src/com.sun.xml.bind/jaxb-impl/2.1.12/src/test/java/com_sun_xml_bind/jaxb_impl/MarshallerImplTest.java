/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import com_sun_xml_bind.jaxb_impl.model.Book;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MarshallerImplTest {
    @Test
    void writesFieldsAdaptersAndLifecycleCallbacksToWriter() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Book.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        Book book = Book.example();
        book.setTitle("Java < XML & friends");
        List<String> listenerEvents = new ArrayList<>();
        marshaller.setListener(new Marshaller.Listener() {
            @Override
            public void beforeMarshal(Object source) {
                if (source == book) {
                    listenerEvents.add("before");
                }
            }

            @Override
            public void afterMarshal(Object source) {
                if (source == book) {
                    listenerEvents.add("after");
                }
            }
        });
        StringWriter xml = new StringWriter();
        marshaller.marshal(book, xml);

        assertTrue(xml.toString().contains("&lt;"));
        assertTrue(xml.toString().contains("&amp;"));
        assertDocument(xml.toString(), "Java < XML & friends");
        assertTrue(book.getEvents().containsAll(List.of("beforeMarshal", "afterMarshal")));
        assertEquals("before", listenerEvents.getFirst());
        assertEquals("after", listenerEvents.getLast());
    }

    @Test
    void writesUtf8OutputStream() throws Exception {
        Marshaller marshaller = JAXBContext.newInstance(Book.class).createMarshaller();
        Book book = Book.example();
        book.setTitle("Caf\u00e9 & XML");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        marshaller.marshal(book, bytes);

        assertDocument(bytes.toString(StandardCharsets.UTF_8), "Caf\u00e9 & XML");
    }

    @Test
    void writesStaxFragmentsWithoutXmlDeclaration() throws Exception {
        Marshaller marshaller = JAXBContext.newInstance(Book.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        StringWriter xml = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(xml);
        try {
            marshaller.marshal(Book.example(), writer);
            writer.flush();
        } finally {
            writer.close();
        }

        assertFalse(xml.toString().contains("<?xml"));
        assertDocument(xml.toString(), "Native & Java");
    }

    private static void assertDocument(String xml, String title) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        assertEquals(Book.NAMESPACE, document.getDocumentElement().getNamespaceURI());
        assertEquals("book", document.getDocumentElement().getLocalName());
        assertEquals("guide", document.getDocumentElement().getAttribute("id"));
        assertEquals(title, document.getElementsByTagName("title").item(0).getTextContent());
        assertEquals("24.50", document.getElementsByTagName("price").item(0).getTextContent());
        assertEquals("978-1234567890", document.getElementsByTagName("isbn").item(0).getTextContent());
        assertEquals(2, document.getElementsByTagName("tag").getLength());
        assertEquals("xml", document.getElementsByTagName("tag").item(0).getTextContent());
        assertEquals("java", document.getElementsByTagName("tag").item(1).getTextContent());
    }
}
