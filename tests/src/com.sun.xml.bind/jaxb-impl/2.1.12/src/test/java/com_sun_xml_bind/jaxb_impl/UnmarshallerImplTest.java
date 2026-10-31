/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;

import com_sun_xml_bind.jaxb_impl.model.Book;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UnmarshallerImplTest {
    @Test
    void readsSaxSourceAndNotifiesListeners() throws Exception {
        Unmarshaller unmarshaller = JAXBContext.newInstance(Book.class).createUnmarshaller();
        List<String> events = new ArrayList<>();
        unmarshaller.setListener(new Unmarshaller.Listener() {
            @Override
            public void beforeUnmarshal(Object target, Object parent) {
                if (target instanceof Book) {
                    assertNull(parent);
                    events.add("before");
                }
            }

            @Override
            public void afterUnmarshal(Object target, Object parent) {
                if (target instanceof Book) {
                    assertNull(parent);
                    events.add("after");
                }
            }
        });

        Book book = assertInstanceOf(Book.class, unmarshaller.unmarshal(new StringReader(Book.XML)));
        assertBook(book);
        assertEquals(List.of("before", "after"), events);
    }

    @Test
    void readsTypedStreamSource() throws Exception {
        Unmarshaller unmarshaller = JAXBContext.newInstance(Book.class).createUnmarshaller();
        JAXBElement<Book> element = unmarshaller.unmarshal(new StreamSource(new StringReader(Book.XML)), Book.class);

        assertEquals(new QName(Book.NAMESPACE, "book"), element.getName());
        assertBook(element.getValue());
    }

    @Test
    void readsStaxStreamReader() throws Exception {
        Unmarshaller unmarshaller = JAXBContext.newInstance(Book.class).createUnmarshaller();
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(Book.XML));
        try {
            assertBook(assertInstanceOf(Book.class, unmarshaller.unmarshal(reader)));
        } finally {
            reader.close();
        }
    }

    @Test
    void readsStaxEventReader() throws Exception {
        Unmarshaller unmarshaller = JAXBContext.newInstance(Book.class).createUnmarshaller();
        XMLEventReader reader = XMLInputFactory.newFactory().createXMLEventReader(new StringReader(Book.XML));
        try {
            JAXBElement<Book> element = unmarshaller.unmarshal(reader, Book.class);
            assertEquals(new QName(Book.NAMESPACE, "book"), element.getName());
            assertBook(element.getValue());
        } finally {
            reader.close();
        }
    }

    private static void assertBook(Book book) {
        assertEquals("guide", book.getId());
        assertEquals("Native & Java", book.getTitle());
        assertEquals(new BigDecimal("24.50"), book.getPrice());
        assertEquals("9781234567890", book.getIsbn().getDigits());
        assertEquals(List.of("xml", "java"), book.getTags());
        assertEquals(List.of("beforeUnmarshal", "afterUnmarshal"), book.getEvents());
    }
}
