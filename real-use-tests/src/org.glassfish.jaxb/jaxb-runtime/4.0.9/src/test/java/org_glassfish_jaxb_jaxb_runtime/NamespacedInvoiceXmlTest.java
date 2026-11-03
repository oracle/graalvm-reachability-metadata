/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.glassfish.jaxb.runtime.marshaller.NamespacePrefixMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's namespace-prefix mapping subsystem for an invoice XML exchange.
public class NamespacedInvoiceXmlTest {

    @Test
    @Timeout(60)
    void writesConsumerPrefixesAndReadsTheInvoiceBack() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Invoice.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty("org.glassfish.jaxb.namespacePrefixMapper", new InvoicePrefixMapper());

        Invoice invoice = new Invoice("INV-1042", List.of(new LineItem("Desk lamp", 2)));
        StringWriter output = new StringWriter();
        marshaller.marshal(invoice, output);

        String xml = output.toString();
        assertTrue(xml.contains("<billing:invoice"));
        assertTrue(xml.contains("xmlns:billing=\"urn:billing\""));
        assertTrue(xml.contains("xmlns:line=\"urn:line\""));
        assertTrue(xml.contains("<line:item>"));
        assertTrue(xml.contains("<line:description>Desk lamp</line:description>"));

        Invoice restored = (Invoice) context.createUnmarshaller()
                .unmarshal(new StringReader(xml));
        assertEquals(invoice.number, restored.number);
        assertEquals(1, restored.items.size());
        assertEquals("Desk lamp", restored.items.get(0).description);
        assertEquals(2, restored.items.get(0).quantity);
    }

    @XmlRootElement(name = "invoice", namespace = Invoice.BILLING_NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Invoice {

        private static final String BILLING_NAMESPACE = "urn:billing";
        private static final String LINE_NAMESPACE = "urn:line";

        @XmlElement
        private String number;

        @XmlElement(name = "item", namespace = LINE_NAMESPACE)
        private List<LineItem> items;

        public Invoice() {
        }

        private Invoice(String number, List<LineItem> items) {
            this.number = number;
            this.items = items;
        }
    }

    @XmlType(name = "lineItem", namespace = Invoice.LINE_NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class LineItem {

        @XmlElement(namespace = Invoice.LINE_NAMESPACE)
        private String description;

        @XmlElement(namespace = Invoice.LINE_NAMESPACE)
        private int quantity;

        public LineItem() {
        }

        private LineItem(String description, int quantity) {
            this.description = description;
            this.quantity = quantity;
        }
    }

    public static final class InvoicePrefixMapper extends NamespacePrefixMapper {

        @Override
        public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
            if (Invoice.BILLING_NAMESPACE.equals(namespaceUri)) {
                return "billing";
            }
            if (Invoice.LINE_NAMESPACE.equals(namespaceUri)) {
                return "line";
            }
            return suggestion;
        }
    }
}
