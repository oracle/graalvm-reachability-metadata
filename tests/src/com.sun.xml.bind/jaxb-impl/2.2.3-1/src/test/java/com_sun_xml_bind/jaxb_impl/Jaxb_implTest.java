/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlMixed;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

public class Jaxb_implTest {
    static {
        System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
    }

    @Test
    void marshalsAnnotatedObjectGraph() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PurchaseOrder.class, Customer.class, LineItem.class, Money.class);
        PurchaseOrder order = sampleOrder();

        String xml = marshal(context, order);

        assertThat(xml).contains("<purchaseOrder");
        assertThat(xml).contains("number=\"PO-123\"");
        assertThat(xml).contains("<orderedOn>2026-05-09</orderedOn>");
        assertThat(xml).contains("sku=\"BK-1\"");
        assertThat(xml).contains("<approver>cust-1</approver>");
        assertThat(xml).doesNotContain("internal-note");
        assertThat(order.beforeMarshalCalled).isTrue();
    }

    @Test
    void marshalsPolymorphicElementLists() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Catalog.class, Book.class, Movie.class);
        Catalog catalog = new Catalog();
        catalog.items.add(new Book("b1", "The Left Hand of Darkness", "Ursula K. Le Guin"));
        catalog.items.add(new Movie("m1", "Spirited Away", 125));

        String xml = marshal(context, catalog);

        assertThat(xml).contains("<book id=\"b1\">");
        assertThat(xml).contains("<author>Ursula K. Le Guin</author>");
        assertThat(xml).contains("<movie id=\"m1\">");
        assertThat(xml).contains("<durationMinutes>125</durationMinutes>");
    }

    @Test
    void marshalsMapValuesWithXmlAdapter() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Settings.class);
        Settings settings = new Settings();
        settings.properties.put("host", "localhost");
        settings.properties.put("port", "8080");

        String xml = marshal(context, settings);

        assertThat(xml).contains("<entry key=\"host\">localhost</entry>");
        assertThat(xml).contains("<entry key=\"port\">8080</entry>");
    }

    @Test
    void marshalsMixedContentWithElementReferences() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Article.class, ArticleObjectFactory.class);
        Article article = new Article();
        ArticleObjectFactory objectFactory = new ArticleObjectFactory();
        article.content.add("Read ");
        article.content.add(objectFactory.createEmphasis("carefully"));
        article.content.add(" before signing.");

        String xml = marshal(context, article);

        assertThat(xml).contains("<article>");
        assertThat(xml).contains("Read ");
        assertThat(xml).contains("<emphasis>carefully</emphasis>");
        assertThat(xml).contains(" before signing.");
    }

    @Test
    void marshalsWildcardDomElements() throws Exception {
        JAXBContext context = JAXBContext.newInstance(MessageEnvelope.class);
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element priority = document.createElementNS("urn:example:message", "priority");
        priority.setTextContent("high");
        Element tag = document.createElement("tag");
        tag.setAttribute("code", "blue");
        tag.setTextContent("external");
        MessageEnvelope envelope = new MessageEnvelope();
        envelope.body = "Outbound message";
        envelope.extensions.add(priority);
        envelope.extensions.add(tag);

        String xml = marshal(context, envelope);

        assertThat(xml).contains("<body>Outbound message</body>");
        assertThat(xml).contains("urn:example:message");
        assertThat(xml).contains(">high</");
        assertThat(xml).contains("<tag code=\"blue\">external</tag>");
    }

    @Test
    void marshalsToDomDocument() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PurchaseOrder.class, Customer.class, LineItem.class, Money.class);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().newDocument();
        Marshaller marshaller = context.createMarshaller();
        marshaller.marshal(sampleOrder(), document);

        assertThat(document.getDocumentElement().getAttribute("number")).isEqualTo("PO-123");
        assertThat(document.getElementsByTagName("status").item(0).getTextContent()).isEqualTo("NEW");
        assertThat(document.getElementsByTagName("quantity").item(0).getTextContent()).isEqualTo("2");
    }

    @Test
    void generatesSchemaForAnnotatedObjectGraph() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PurchaseOrder.class, Customer.class, LineItem.class, Money.class);
        List<StringWriter> schemaWriters = new ArrayList<>();
        context.generateSchema(new SchemaOutputResolver() {
            @Override
            public Result createOutput(String namespaceUri, String suggestedFileName) {
                StringWriter writer = new StringWriter();
                schemaWriters.add(writer);
                StreamResult result = new StreamResult(writer);
                result.setSystemId(suggestedFileName);
                return result;
            }
        });

        assertThat(schemaWriters).hasSize(1);
        String schemaText = schemaWriters.get(0).toString();
        assertThat(schemaText).contains("purchaseOrder");
        assertThat(schemaText).contains("lineItems");
        assertThat(schemaText).contains("orderedOn");

        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(schemaText)));
        assertThat(schema).isNotNull();
    }

    private static String marshal(JAXBContext context, Object value) throws Exception {
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        StringWriter writer = new StringWriter();
        marshaller.marshal(value, writer);
        return writer.toString();
    }

    private static PurchaseOrder sampleOrder() {
        Customer customer = new Customer("cust-1", "Ada Lovelace");
        PurchaseOrder order = new PurchaseOrder();
        order.number = "PO-123";
        order.customer = customer;
        order.approver = customer;
        order.orderedOn = LocalDate.of(2026, 5, 9);
        order.status = OrderStatus.NEW;
        order.internalNote = "internal-note";
        order.lineItems.add(new LineItem("BK-1", "Book", 2));
        order.lineItems.add(new LineItem("PEN-7", "Pen", 1));
        order.total = new Money("USD", new BigDecimal("42.50"));
        return order;
    }

    public enum OrderStatus {
        NEW,
        APPROVED
    }

    @XmlRootElement(name = "purchaseOrder")
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"customer", "approver", "orderedOn", "status", "lineItems", "total"})
    public static class PurchaseOrder {
        @XmlAttribute
        public String number;

        @XmlElement(required = true)
        public Customer customer;

        @XmlIDREF
        @XmlElement(required = true)
        public Customer approver;

        @XmlJavaTypeAdapter(LocalDateAdapter.class)
        public LocalDate orderedOn;

        public OrderStatus status;

        @XmlElementWrapper(name = "lineItems")
        @XmlElement(name = "item")
        public List<LineItem> lineItems = new ArrayList<>();

        public Money total;

        @XmlTransient
        public String internalNote;

        @XmlTransient
        public boolean beforeMarshalCalled;

        @XmlTransient
        public boolean afterUnmarshalCalled;

        public PurchaseOrder() {
        }

        public void beforeMarshal(Marshaller marshaller) {
            beforeMarshalCalled = true;
        }

        public void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
            afterUnmarshalCalled = true;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Customer {
        @XmlID
        @XmlAttribute
        public String id;

        @XmlElement(required = true)
        public String name;

        public Customer() {
        }

        public Customer(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class LineItem {
        @XmlAttribute
        public String sku;

        public String description;

        public int quantity;

        public LineItem() {
        }

        public LineItem(String sku, String description, int quantity) {
            this.sku = sku;
            this.description = description;
            this.quantity = quantity;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Money {
        @XmlAttribute
        public String currency;

        public BigDecimal amount;

        public Money() {
        }

        public Money(String currency, BigDecimal amount) {
            this.currency = currency;
            this.amount = amount;
        }
    }

    public static class LocalDateAdapter extends XmlAdapter<String, LocalDate> {
        @Override
        public LocalDate unmarshal(String value) {
            return LocalDate.parse(value);
        }

        @Override
        public String marshal(LocalDate value) {
            return value.toString();
        }
    }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Catalog {
        @XmlElements({
                @XmlElement(name = "book", type = Book.class),
                @XmlElement(name = "movie", type = Movie.class)
        })
        public List<CatalogItem> items = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public abstract static class CatalogItem {
        @XmlAttribute
        public String id;

        public CatalogItem() {
        }

        protected CatalogItem(String id) {
            this.id = id;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book extends CatalogItem {
        public String title;

        public String author;

        public Book() {
        }

        public Book(String id, String title, String author) {
            super(id);
            this.title = title;
            this.author = author;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Movie extends CatalogItem {
        public String title;

        public int durationMinutes;

        public Movie() {
        }

        public Movie(String id, String title, int durationMinutes) {
            super(id);
            this.title = title;
            this.durationMinutes = durationMinutes;
        }
    }

    @XmlRootElement(name = "article")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Article {
        @XmlMixed
        @XmlElementRef(name = "emphasis", type = JAXBElement.class)
        public List<Object> content = new ArrayList<>();
    }

    @XmlRegistry
    public static class ArticleObjectFactory {
        @XmlElementDecl(name = "emphasis")
        public JAXBElement<String> createEmphasis(String value) {
            return new JAXBElement<>(new QName("emphasis"), String.class, value);
        }
    }

    @XmlRootElement(name = "message")
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"body", "extensions"})
    public static class MessageEnvelope {
        public String body;

        @XmlAnyElement
        public List<Element> extensions = new ArrayList<>();
    }

    @XmlRootElement(name = "settings")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Settings {
        @XmlJavaTypeAdapter(PropertiesAdapter.class)
        public Map<String, String> properties = new LinkedHashMap<>();
    }

    public static class PropertiesAdapter extends XmlAdapter<AdaptedProperties, Map<String, String>> {
        @Override
        public Map<String, String> unmarshal(AdaptedProperties value) {
            Map<String, String> result = new LinkedHashMap<>();
            for (PropertyEntry entry : value.entries) {
                result.put(entry.key, entry.value);
            }
            return result;
        }

        @Override
        public AdaptedProperties marshal(Map<String, String> value) {
            AdaptedProperties properties = new AdaptedProperties();
            for (Map.Entry<String, String> entry : value.entrySet()) {
                properties.entries.add(new PropertyEntry(entry.getKey(), entry.getValue()));
            }
            return properties;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AdaptedProperties {
        @XmlElement(name = "entry")
        public List<PropertyEntry> entries = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PropertyEntry {
        @XmlAttribute
        public String key;

        @XmlValue
        public String value;

        public PropertyEntry() {
        }

        public PropertyEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
