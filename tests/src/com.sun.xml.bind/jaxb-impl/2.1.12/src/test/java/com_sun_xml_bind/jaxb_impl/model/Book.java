/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "book", namespace = Book.NAMESPACE)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "bookType", propOrder = {"title", "price", "isbn", "tags"})
public class Book {
    public static final String NAMESPACE = "urn:forge:books";
    public static final String XML = """
            <b:book xmlns:b="urn:forge:books" id="guide">
              <title>Native &amp; Java</title>
              <price>24.50</price>
              <isbn>978-1234567890</isbn>
              <tags><tag>xml</tag><tag>java</tag></tags>
            </b:book>
            """;

    @XmlAttribute(required = true)
    private String id;
    @XmlElement(required = true)
    private String title;
    private BigDecimal price;
    @XmlJavaTypeAdapter(IsbnAdapter.class)
    private Isbn isbn;
    @XmlElementWrapper(name = "tags")
    @XmlElement(name = "tag")
    private List<String> tags = new ArrayList<>();
    @XmlTransient
    private List<String> events = new ArrayList<>();

    public Book() {
    }

    public static Book example() {
        Book book = new Book();
        book.id = "guide";
        book.title = "Native & Java";
        book.price = new BigDecimal("24.50");
        book.isbn = new Isbn("9781234567890");
        book.tags.addAll(List.of("xml", "java"));
        return book;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Isbn getIsbn() {
        return isbn;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getEvents() {
        return events;
    }

    private void beforeMarshal(Marshaller marshaller) {
        events.add("beforeMarshal");
    }

    private void afterMarshal(Marshaller marshaller) {
        events.add("afterMarshal");
    }

    private void beforeUnmarshal(Unmarshaller unmarshaller, Object parent) {
        events.add("beforeUnmarshal");
    }

    private void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
        events.add("afterUnmarshal");
    }

    public static final class Isbn {
        private final String digits;

        public Isbn(String digits) {
            this.digits = digits;
        }

        public String getDigits() {
            return digits;
        }
    }

    public static class IsbnAdapter extends XmlAdapter<String, Isbn> {
        @Override
        public Isbn unmarshal(String value) {
            return value == null ? null : new Isbn(value.replace("-", ""));
        }

        @Override
        public String marshal(Isbn value) {
            return value == null ? null : value.getDigits().substring(0, 3) + "-" + value.getDigits().substring(3);
        }
    }
}
