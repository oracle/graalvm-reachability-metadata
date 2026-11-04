/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import java.io.StringReader;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilderFactory;

import com_sun_xml_bind.jaxb_impl.model.Book;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

public class BinderImplTest {
    @Test
    void updatesAssociatedJavaObjectsAndWritesChangesBackToXml() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(Book.XML)));
        Binder<Node> binder = JAXBContext.newInstance(Book.class).createBinder();
        Book book = assertInstanceOf(Book.class, binder.unmarshal(document));

        assertEquals("Native & Java", book.getTitle());
        assertSame(document.getDocumentElement(), binder.getXMLNode(book));
        assertSame(book, binder.getJAXBNode(document.getDocumentElement()));

        document.getElementsByTagName("title").item(0).setTextContent("Updated from XML");
        Book rebound = assertInstanceOf(Book.class, binder.updateJAXB(document.getDocumentElement()));
        assertSame(book, rebound);
        assertEquals("Updated from XML", book.getTitle());
        assertEquals("9781234567890", book.getIsbn().getDigits());

        book.setTitle("Updated from Java");
        Node updated = binder.updateXML(book);
        assertSame(document.getDocumentElement(), updated);
        assertEquals("Updated from Java", document.getElementsByTagName("title").item(0).getTextContent());
    }

    @Test
    void marshalsIntoDom() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Binder<Node> binder = JAXBContext.newInstance(Book.class).createBinder(Node.class);
        Book book = Book.example();
        binder.marshal(book, document);

        Element root = document.getDocumentElement();
        assertEquals(Book.NAMESPACE, root.getNamespaceURI());
        assertEquals("book", root.getLocalName());
        assertEquals("guide", root.getAttribute("id"));
        assertEquals("Native & Java", document.getElementsByTagName("title").item(0).getTextContent());
        assertEquals("978-1234567890", document.getElementsByTagName("isbn").item(0).getTextContent());
        assertEquals(2, document.getElementsByTagName("tag").getLength());
    }
}
