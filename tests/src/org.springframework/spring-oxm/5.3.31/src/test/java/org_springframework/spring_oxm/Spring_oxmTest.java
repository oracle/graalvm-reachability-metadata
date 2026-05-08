/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_oxm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlMimeType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.oxm.XmlMappingException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.oxm.mime.MimeContainer;
import org.springframework.oxm.support.MarshallingSource;
import org.springframework.oxm.support.SaxResourceUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Spring_oxmTest {
    private static final String INVOICE_SCHEMA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="unqualified">
                <xs:element name="invoice">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="customer" type="xs:string" />
                            <xs:element name="total">
                                <xs:complexType>
                                    <xs:simpleContent>
                                        <xs:extension base="xs:decimal">
                                            <xs:attribute name="currency" type="xs:string" use="required" />
                                        </xs:extension>
                                    </xs:simpleContent>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="lineItems">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="lineItem" maxOccurs="unbounded">
                                            <xs:complexType>
                                                <xs:sequence>
                                                    <xs:element name="quantity" type="xs:int" />
                                                </xs:sequence>
                                                <xs:attribute name="sku" type="xs:string" use="required" />
                                            </xs:complexType>
                                        </xs:element>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:string" use="required" />
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void jaxbMarshallerRoundTripsAnnotatedObjectWithAdapterAndListeners() throws Exception {
        Jaxb2Marshaller marshaller = newInvoiceMarshaller();
        AtomicInteger marshalCount = new AtomicInteger();
        AtomicInteger unmarshalCount = new AtomicInteger();
        marshaller.setMarshallerListener(new Marshaller.Listener() {
            @Override
            public void beforeMarshal(Object source) {
                marshalCount.incrementAndGet();
            }
        });
        marshaller.setUnmarshallerListener(new Unmarshaller.Listener() {
            @Override
            public void afterUnmarshal(Object target, Object parent) {
                unmarshalCount.incrementAndGet();
            }
        });

        String xml = marshalToString(marshaller, sampleInvoice());
        assertThat(xml)
                .contains("<invoice id=\"INV-7\">")
                .contains("<customer>Ada Lovelace</customer>")
                .contains("<total currency=\"EUR\">42.50</total>")
                .contains("<lineItem sku=\"SPR-OXM\">");
        assertThat(marshalCount.get()).isGreaterThanOrEqualTo(1);

        Invoice invoice = (Invoice) marshaller.unmarshal(new StreamSource(new StringReader(xml)));

        assertThat(unmarshalCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(invoice.getId()).isEqualTo("INV-7");
        assertThat(invoice.getCustomer()).isEqualTo("Ada Lovelace");
        assertThat(invoice.getTotal()).isEqualTo(new Money("EUR", new BigDecimal("42.50")));
        assertThat(invoice.getLineItems()).extracting(LineItem::getSku).containsExactly("SPR-OXM", "JAXB");
        assertThat(invoice.getLineItems()).extracting(LineItem::getQuantity).containsExactly(2, 3);
    }

    @Test
    void jaxbMarshallerHandlesDomAndStaxSources() throws Exception {
        Jaxb2Marshaller marshaller = newInvoiceMarshaller();
        DOMResult domResult = new DOMResult();

        marshaller.marshal(sampleInvoice(), domResult);

        assertThat(domResult.getNode()).isNotNull();
        Invoice domInvoice = (Invoice) marshaller.unmarshal(new DOMSource(domResult.getNode()));
        assertThat(domInvoice.getId()).isEqualTo("INV-7");
        assertThat(domInvoice.getTotal().getCurrency()).isEqualTo("EUR");

        XMLStreamReader streamReader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(marshalToString(marshaller, sampleInvoice())));
        try {
            Invoice staxInvoice = (Invoice) marshaller.unmarshal(new StAXSource(streamReader));
            assertThat(staxInvoice.getLineItems()).hasSize(2);
            assertThat(staxInvoice.getLineItems().get(0).getSku()).isEqualTo("SPR-OXM");
        }
        finally {
            streamReader.close();
        }
    }

    @Test
    void marshallingSourceActsAsSaxSourceForJaxpTransformers() throws Exception {
        Jaxb2Marshaller marshaller = newInvoiceMarshaller();
        Invoice invoice = sampleInvoice();
        MarshallingSource source = new MarshallingSource(marshaller, invoice);
        StringWriter writer = new StringWriter();

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(source, new StreamResult(writer));

        assertThat(source.getMarshaller()).isSameAs(marshaller);
        assertThat(source.getContent()).isSameAs(invoice);
        assertThat(writer.toString())
                .contains("<invoice id=\"INV-7\">")
                .contains("<customer>Ada Lovelace</customer>")
                .contains("<lineItems>");
    }

    @Test
    void schemaValidationRejectsInvalidXml() throws Exception {
        Jaxb2Marshaller marshaller = newInvoiceMarshaller();
        marshaller.setSchema(new ByteArrayResource(INVOICE_SCHEMA.getBytes(StandardCharsets.UTF_8)));
        marshaller.afterPropertiesSet();
        String invalidXml = """
                <invoice>
                    <customer>Ada Lovelace</customer>
                    <total>42.50</total>
                    <lineItems>
                        <lineItem sku="SPR-OXM"><quantity>2</quantity></lineItem>
                    </lineItems>
                </invoice>
                """;

        assertThatThrownBy(() -> marshaller.unmarshal(new StreamSource(new StringReader(invalidXml))))
                .isInstanceOf(XmlMappingException.class)
                .hasMessageContaining("JAXB");
    }

    @Test
    void dtdAndExternalEntityProcessingAreExplicitlyConfigurable(@TempDir Path tempDir) throws Exception {
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "expanded-value", StandardCharsets.UTF_8);
        String xml = """
                <!DOCTYPE textMessage [
                    <!ENTITY secret SYSTEM "%s">
                ]>
                <textMessage><text>&secret;</text></textMessage>
                """.formatted(secret.toUri());

        Jaxb2Marshaller safeMarshaller = newTextMessageMarshaller(false, false);
        assertThatThrownBy(() -> safeMarshaller.unmarshal(new StreamSource(new StringReader(xml))))
                .isInstanceOf(XmlMappingException.class);

        Jaxb2Marshaller permissiveMarshaller = newTextMessageMarshaller(true, true);
        TextMessage message = (TextMessage) permissiveMarshaller.unmarshal(new StreamSource(new StringReader(xml)));

        assertThat(message.getText()).isEqualTo("expanded-value");
    }

    @Test
    void saxResourceUtilsCreatesInputSourceWithReadableStreamAndSystemId(@TempDir Path tempDir) throws Exception {
        Path xmlFile = tempDir.resolve("message.xml");
        Files.writeString(xmlFile, "<textMessage><text>hello</text></textMessage>", StandardCharsets.UTF_8);
        FileSystemResource resource = new FileSystemResource(xmlFile.toFile());

        InputSource inputSource = SaxResourceUtils.createInputSource(resource);

        assertThat(inputSource.getSystemId()).isEqualTo(xmlFile.toUri().toString());
        try (InputStream stream = inputSource.getByteStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("<textMessage><text>hello</text></textMessage>");
        }
    }

    @Test
    void jaxbMarshallerRoundTripsMtomAttachmentThroughMimeContainer() throws Exception {
        Jaxb2Marshaller marshaller = newBinaryDocumentMarshaller();
        byte[] payload = "portable binary payload".getBytes(StandardCharsets.UTF_8);
        BinaryDocument document = new BinaryDocument("diagram.bin",
                new DataHandler(new ByteArrayDataSource("diagram.bin", "application/octet-stream", payload)));
        RecordingMimeContainer mimeContainer = new RecordingMimeContainer();
        StringWriter writer = new StringWriter();

        marshaller.marshal(document, new StreamResult(writer), mimeContainer);

        String xml = writer.toString();
        assertThat(xml)
                .contains("binaryDocument")
                .contains("name=\"diagram.bin\"")
                .contains("xop:Include")
                .contains("cid:");
        assertThat(mimeContainer.getAttachmentCount()).isEqualTo(1);

        BinaryDocument restored = (BinaryDocument) marshaller.unmarshal(new StreamSource(new StringReader(xml)),
                mimeContainer);

        assertThat(restored.getName()).isEqualTo("diagram.bin");
        assertThat(readBytes(restored.getContent())).isEqualTo(payload);
        assertThat(restored.getContent().getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void jaxbMarshallerUnmarshalsMappedClassWithoutXmlRootElement() throws Exception {
        Jaxb2Marshaller marshaller = newCustomerRecordMarshaller();
        String xml = """
                <customerRecord>
                    <name>Grace Hopper</name>
                    <active>true</active>
                </customerRecord>
                """;

        CustomerRecord customerRecord = (CustomerRecord) marshaller.unmarshal(new StreamSource(new StringReader(xml)));

        assertThat(customerRecord.getName()).isEqualTo("Grace Hopper");
        assertThat(customerRecord.isActive()).isTrue();
        assertThat(marshaller.supports(CustomerRecord.class)).isTrue();
        assertThat(marshaller.supports(TextMessage.class)).isFalse();
    }

    private static Jaxb2Marshaller newInvoiceMarshaller() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(Invoice.class, LineItem.class, MoneyValue.class);
        marshaller.setAdapters(new MoneyAdapter());
        marshaller.setMarshallerProperties(Collections.singletonMap(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE));
        marshaller.afterPropertiesSet();
        return marshaller;
    }

    private static Jaxb2Marshaller newTextMessageMarshaller(boolean supportDtd, boolean processExternalEntities)
            throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(TextMessage.class);
        marshaller.setSupportDtd(supportDtd);
        marshaller.setProcessExternalEntities(processExternalEntities);
        marshaller.afterPropertiesSet();
        return marshaller;
    }

    private static Jaxb2Marshaller newBinaryDocumentMarshaller() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(BinaryDocument.class);
        marshaller.setMtomEnabled(true);
        marshaller.afterPropertiesSet();
        return marshaller;
    }

    private static Jaxb2Marshaller newCustomerRecordMarshaller() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(CustomerRecord.class);
        marshaller.setMappedClass(CustomerRecord.class);
        marshaller.setCheckForXmlRootElement(false);
        marshaller.afterPropertiesSet();
        return marshaller;
    }

    private static String marshalToString(Jaxb2Marshaller marshaller, Object value) {
        StringWriter writer = new StringWriter();
        marshaller.marshal(value, new StreamResult(writer));
        return writer.toString();
    }

    private static Invoice sampleInvoice() {
        return new Invoice("INV-7", "Ada Lovelace", new Money("EUR", new BigDecimal("42.50")),
                Arrays.asList(new LineItem("SPR-OXM", 2), new LineItem("JAXB", 3)));
    }

    private static byte[] readBytes(DataHandler dataHandler) throws IOException {
        try (InputStream inputStream = dataHandler.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    @XmlRootElement(name = "invoice")
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"customer", "total", "lineItems"})
    public static class Invoice {
        @XmlAttribute(required = true)
        private String id;

        @XmlElement(required = true)
        private String customer;

        @XmlElement(required = true)
        @XmlJavaTypeAdapter(MoneyAdapter.class)
        private Money total;

        @XmlElementWrapper(name = "lineItems")
        @XmlElement(name = "lineItem")
        private List<LineItem> lineItems = new ArrayList<>();

        public Invoice() {
        }

        Invoice(String id, String customer, Money total, List<LineItem> lineItems) {
            this.id = id;
            this.customer = customer;
            this.total = total;
            this.lineItems = new ArrayList<>(lineItems);
        }

        public String getId() {
            return id;
        }

        public String getCustomer() {
            return customer;
        }

        public Money getTotal() {
            return total;
        }

        public List<LineItem> getLineItems() {
            return lineItems;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class LineItem {
        @XmlAttribute(required = true)
        private String sku;

        @XmlElement(required = true)
        private int quantity;

        public LineItem() {
        }

        LineItem(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() {
            return sku;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    public static class Money {
        private final String currency;
        private final BigDecimal amount;

        Money(String currency, BigDecimal amount) {
            this.currency = currency;
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Money)) {
                return false;
            }
            Money money = (Money) other;
            return Objects.equals(currency, money.currency) && amount.compareTo(money.amount) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(currency, amount.stripTrailingZeros());
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MoneyValue {
        @XmlAttribute(required = true)
        private String currency;

        @XmlValue
        private BigDecimal amount;

        public MoneyValue() {
        }

        MoneyValue(String currency, BigDecimal amount) {
            this.currency = currency;
            this.amount = amount;
        }
    }

    public static class MoneyAdapter extends XmlAdapter<MoneyValue, Money> {
        @Override
        public Money unmarshal(MoneyValue value) {
            return new Money(value.currency, value.amount);
        }

        @Override
        public MoneyValue marshal(Money value) {
            return new MoneyValue(value.getCurrency(), value.getAmount());
        }
    }

    @XmlRootElement(name = "binaryDocument")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BinaryDocument {
        @XmlAttribute(required = true)
        private String name;

        @XmlElement(required = true)
        @XmlMimeType("application/octet-stream")
        private DataHandler content;

        public BinaryDocument() {
        }

        BinaryDocument(String name, DataHandler content) {
            this.name = name;
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public DataHandler getContent() {
            return content;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "customerRecord", propOrder = {"name", "active"})
    public static class CustomerRecord {
        @XmlElement(required = true)
        private String name;

        @XmlElement
        private boolean active;

        public CustomerRecord() {
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }
    }

    private static final class ByteArrayDataSource implements DataSource {
        private final String name;
        private final String contentType;
        private final byte[] content;

        ByteArrayDataSource(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = Arrays.copyOf(content, content.length);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            throw new IOException("This data source is read-only");
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class RecordingMimeContainer implements MimeContainer {
        private final Map<String, DataHandler> attachments = new LinkedHashMap<>();

        @Override
        public boolean isXopPackage() {
            return true;
        }

        @Override
        public DataHandler getAttachment(String contentId) {
            return attachments.get(normalizeContentId(contentId));
        }

        @Override
        public String addAttachment(DataHandler dataHandler) {
            String contentId = "attachment-" + attachments.size();
            attachments.put(contentId, dataHandler);
            return "cid:" + contentId;
        }

        int getAttachmentCount() {
            return attachments.size();
        }

        private static String normalizeContentId(String contentId) {
            String normalized = contentId;
            if (normalized.startsWith("cid:")) {
                normalized = normalized.substring("cid:".length());
            }
            if (normalized.startsWith("<") && normalized.endsWith(">")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            return normalized;
        }
    }

    @XmlRootElement(name = "textMessage")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TextMessage {
        @XmlElement
        private String text;

        public TextMessage() {
        }

        public String getText() {
            return text;
        }
    }
}
