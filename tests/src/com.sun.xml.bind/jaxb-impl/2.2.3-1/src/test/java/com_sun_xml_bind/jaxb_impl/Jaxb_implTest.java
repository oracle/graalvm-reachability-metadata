/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.sun.xml.bind.CycleRecoverable;
import com.sun.xml.bind.IDResolver;
import com.sun.xml.bind.api.Bridge;
import com.sun.xml.bind.api.JAXBRIContext;
import com.sun.xml.bind.api.TypeReference;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class Jaxb_implTest {
    private static final String NAMESPACE_URI = "urn:test:purchase-order";
    private static final QName ITEM_ELEMENT = new QName(NAMESPACE_URI, "item");

    @Test
    void marshalsAndUnmarshalsAnnotatedObjectGraph() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(PurchaseOrder.class);
        PurchaseOrder order = sampleOrder();

        String xml = marshal(context, order);

        assertThat(xml)
                .contains("order")
                .contains("id=\"PO-1\"")
                .contains("customer")
                .contains("customer-1")
                .contains("orderedOn>2026-06-08</")
                .contains("status>APPROVED</")
                .contains("quantity=\"2\"")
                .contains("sku=\"SKU-1\"")
                .contains("Graphite &amp; Paper");

        PurchaseOrder unmarshalled = (PurchaseOrder) context.createUnmarshaller().unmarshal(new StringReader(xml));

        assertThat(unmarshalled.id).isEqualTo("PO-1");
        assertThat(unmarshalled.customer.id).isEqualTo("customer-1");
        assertThat(unmarshalled.customer.name).isEqualTo("Ada Lovelace");
        assertThat(unmarshalled.orderedOn).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(unmarshalled.status).isEqualTo(Status.APPROVED);
        assertThat(unmarshalled.items)
                .extracting(item -> item.sku)
                .containsExactly("SKU-1", "SKU-2");
    }

    @Test
    void riMarshallerPropertiesCustomizeNamespacesAndEscaping() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(PurchaseOrder.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty("com.sun.xml.bind.indentString", "    ");
        marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new PurchaseOrderPrefixMapper());
        marshaller.setProperty("com.sun.xml.bind.characterEscapeHandler", new ApostropheEscapingHandler());

        PurchaseOrder order = sampleOrder();
        order.customer.name = "Alice's & Co";

        StringWriter writer = new StringWriter();
        marshaller.marshal(order, writer);
        String xml = writer.toString();

        assertThat(xml)
                .contains("xmlns:po=\"" + NAMESPACE_URI + "\"")
                .contains("<po:customer")
                .contains("\n    <po:customer")
                .contains("Alice&apos;s &amp; Co");
    }

    @Test
    void jaxbRiContextBridgeMarshalsAndUnmarshalsElementReference() throws JAXBException, XMLStreamException {
        TypeReference itemReference = new TypeReference(ITEM_ELEMENT, LineItem.class);
        JAXBRIContext context = JAXBRIContext.newInstance(
                new Class<?>[] { LineItem.class },
                Collections.singletonList(itemReference),
                Collections.emptyMap(),
                null,
                false,
                null);
        @SuppressWarnings("unchecked")
        Bridge<LineItem> bridge = context.createBridge(itemReference);

        LineItem item = new LineItem("BRIDGE-1", 4, "Bridge Item");
        StringWriter writer = new StringWriter();
        XMLStreamWriter streamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
        bridge.marshal(item, streamWriter);
        streamWriter.close();

        String xml = writer.toString();
        assertThat(xml)
                .contains("item")
                .contains("sku=\"BRIDGE-1\"")
                .contains("quantity=\"4\"")
                .contains("description>Bridge Item</");
        assertThat(context.getKnownNamespaceURIs()).contains(NAMESPACE_URI);
        assertThat(context.getTypeName(itemReference)).isNotNull();

        LineItem unmarshalled = bridge.unmarshal(new StreamSource(new StringReader(xml)));
        assertThat(unmarshalled.sku).isEqualTo("BRIDGE-1");
        assertThat(unmarshalled.quantity).isEqualTo(4);
        assertThat(unmarshalled.description).isEqualTo("Bridge Item");
    }

    @Test
    void unmarshallerUsesCustomIdResolverForIdrefProperties() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(Directory.class, Person.class);
        Person external = new Person("external", "External Reviewer");
        RecordingIdResolver resolver = new RecordingIdResolver(external);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setProperty(IDResolver.class.getName(), resolver);

        Directory directory = (Directory) unmarshaller.unmarshal(new StringReader("""
                <directory>
                    <reviewer>external</reviewer>
                </directory>
                """));

        assertThat(directory.reviewer).isSameAs(external);
        assertThat(resolver.started).isTrue();
        assertThat(resolver.resolvedIds).containsExactly("external");
        assertThat(resolver.ended).isTrue();
    }

    @Test
    void cycleRecoverableSuppliesReplacementForObjectGraphCycles() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(CycleNode.class);
        CycleNode root = new CycleNode("root");
        root.child = root;

        String xml = marshal(context, root);

        assertThat(xml)
                .contains("<node name=\"root\">")
                .contains("child name=\"cycle-root\"");
    }

    @Test
    void generatesSchemaForAnnotatedModel() throws IOException, JAXBException {
        JAXBRIContext context = (JAXBRIContext) JAXBContext.newInstance(PurchaseOrder.class);
        CapturingSchemaOutputResolver resolver = new CapturingSchemaOutputResolver();

        context.generateSchema(resolver);

        assertThat(resolver.schemaText())
                .contains("targetNamespace=\"" + NAMESPACE_URI + "\"")
                .contains("name=\"order\"")
                .contains("name=\"customer\"")
                .contains("name=\"item\"");
    }

    private static String marshal(JAXBContext context, Object value) throws JAXBException {
        StringWriter writer = new StringWriter();
        context.createMarshaller().marshal(value, writer);
        return writer.toString();
    }

    private static PurchaseOrder sampleOrder() {
        PurchaseOrder order = new PurchaseOrder();
        order.id = "PO-1";
        order.customer = new Customer("customer-1", "Ada Lovelace");
        order.orderedOn = LocalDate.of(2026, 6, 8);
        order.status = Status.APPROVED;
        order.items.add(new LineItem("SKU-1", 2, "Graphite & Paper"));
        order.items.add(new LineItem("SKU-2", 1, "Notebook"));
        return order;
    }

    @XmlRootElement(name = "order", namespace = NAMESPACE_URI)
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = { "customer", "orderedOn", "status", "items" })
    public static class PurchaseOrder {
        @XmlAttribute
        private String id;

        @XmlElement(namespace = NAMESPACE_URI, required = true)
        private Customer customer;

        @XmlElement(namespace = NAMESPACE_URI, required = true)
        @XmlJavaTypeAdapter(LocalDateAdapter.class)
        private LocalDate orderedOn;

        @XmlElement(namespace = NAMESPACE_URI, required = true)
        private Status status;

        @XmlElementWrapper(name = "items", namespace = NAMESPACE_URI)
        @XmlElement(name = "item", namespace = NAMESPACE_URI)
        private List<LineItem> items = new ArrayList<>();

        public PurchaseOrder() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = { "name" })
    public static class Customer {
        @XmlAttribute
        private String id;

        @XmlElement(namespace = NAMESPACE_URI, required = true)
        private String name;

        public Customer() {
        }

        Customer(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "lineItem", namespace = NAMESPACE_URI, propOrder = { "description" })
    public static class LineItem {
        @XmlAttribute
        private String sku;

        @XmlAttribute
        private int quantity;

        @XmlElement(namespace = NAMESPACE_URI, required = true)
        private String description;

        public LineItem() {
        }

        LineItem(String sku, int quantity, String description) {
            this.sku = sku;
            this.quantity = quantity;
            this.description = description;
        }
    }

    public enum Status {
        APPROVED,
        PENDING
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

    private static final class PurchaseOrderPrefixMapper extends NamespacePrefixMapper {
        @Override
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if (NAMESPACE_URI.equals(namespaceUri)) {
                return "po";
            }
            return suggestion;
        }
    }

    private static final class ApostropheEscapingHandler implements CharacterEscapeHandler {
        @Override
        public void escape(char[] characters, int start, int length, boolean isAttribute, Writer writer) throws IOException {
            int end = start + length;
            for (int index = start; index < end; index++) {
                char character = characters[index];
                if (character == '&') {
                    writer.write("&amp;");
                } else if (character == '<') {
                    writer.write("&lt;");
                } else if (character == '>') {
                    writer.write("&gt;");
                } else if (character == '\"' && isAttribute) {
                    writer.write("&quot;");
                } else if (character == '\'') {
                    writer.write("&apos;");
                } else {
                    writer.write(character);
                }
            }
        }
    }

    @XmlRootElement(name = "directory")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Directory {
        @XmlElement
        private Person owner;

        @XmlIDREF
        @XmlElement
        private Person reviewer;

        public Directory() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Person {
        @XmlID
        @XmlAttribute
        private String id;

        @XmlElement
        private String name;

        public Person() {
        }

        Person(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class RecordingIdResolver extends IDResolver {
        private final Person resolvedPerson;
        private final List<String> resolvedIds = new ArrayList<>();
        private boolean started;
        private boolean ended;

        private RecordingIdResolver(Person resolvedPerson) {
            this.resolvedPerson = resolvedPerson;
        }

        @Override
        public void startDocument(javax.xml.bind.ValidationEventHandler eventHandler) throws SAXException {
            started = true;
        }

        @Override
        public void endDocument() throws SAXException {
            ended = true;
        }

        @Override
        public void bind(String id, Object object) throws SAXException {
        }

        @Override
        public Callable<?> resolve(String id, Class targetType) throws SAXException {
            resolvedIds.add(id);
            return new ResolvedPerson(resolvedPerson);
        }
    }

    private static final class ResolvedPerson implements Callable<Person> {
        private final Person person;

        private ResolvedPerson(Person person) {
            this.person = person;
        }

        @Override
        public Person call() {
            return person;
        }
    }

    @XmlRootElement(name = "node")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CycleNode implements CycleRecoverable {
        @XmlAttribute
        private String name;

        @XmlElement
        private CycleNode child;

        public CycleNode() {
        }

        CycleNode(String name) {
            this.name = name;
        }

        @Override
        public Object onCycleDetected(CycleRecoverable.Context context) {
            return new CycleNode("cycle-" + name);
        }
    }

    private static final class CapturingSchemaOutputResolver extends SchemaOutputResolver {
        private final Map<String, StringWriter> schemas = new LinkedHashMap<>();

        @Override
        public Result createOutput(String namespaceUri, String suggestedFileName) {
            StringWriter writer = new StringWriter();
            schemas.put(namespaceUri, writer);
            StreamResult result = new StreamResult(writer);
            result.setSystemId(suggestedFileName);
            return result;
        }

        private String schemaText() {
            return schemas.values().stream()
                    .map(StringWriter::toString)
                    .reduce("", String::concat);
        }
    }
}
