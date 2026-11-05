/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import javax.xml.namespace.QName;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.glassfish.jaxb.runtime.api.JAXBRIContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's XmlAdapter subsystem for a currency-aware invoice workflow.
public class XmlAdapterInvoiceTest {

    private static final String BILLING_NAMESPACE = "urn:billing";

    @Test
    @Timeout(60)
    void marshalsAndUnmarshalsTheApplicationMoneyValue() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Invoice.class);
        JAXBRIContext runtimeContext = assertInstanceOf(JAXBRIContext.class, context);
        assertEquals(new QName(BILLING_NAMESPACE, "invoice"),
                runtimeContext.getElementName(Invoice.class));

        Invoice invoice = new Invoice("INV-2048", new Money("USD", new BigDecimal("129.95")));
        Marshaller marshaller = context.createMarshaller();
        StringWriter output = new StringWriter();
        marshaller.marshal(invoice, output);

        String xml = output.toString();
        assertTrue(xml.contains("<number>INV-2048</number>"));
        assertTrue(xml.contains("<total>USD 129.95</total>"));

        Invoice restored = assertInstanceOf(Invoice.class,
                context.createUnmarshaller().unmarshal(new StringReader(xml)));
        assertEquals("INV-2048", restored.number);
        assertEquals("USD", restored.total.currency);
        assertEquals(new BigDecimal("129.95"), restored.total.amount);
    }

    public static final class MoneyAdapter extends XmlAdapter<String, Money> {

        @Override
        public Money unmarshal(String value) {
            String[] parts = value.split(" ", 2);
            return new Money(parts[0], new BigDecimal(parts[1]));
        }

        @Override
        public String marshal(Money value) {
            return value.currency + " " + value.amount.toPlainString();
        }
    }

    public static final class Money {

        private final String currency;
        private final BigDecimal amount;

        public Money() {
            this.currency = null;
            this.amount = null;
        }

        private Money(String currency, BigDecimal amount) {
            this.currency = currency;
            this.amount = amount;
        }
    }

    @XmlRootElement(name = "invoice", namespace = BILLING_NAMESPACE)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Invoice {

        @XmlElement(required = true)
        private String number;

        @XmlElement(required = true)
        @XmlJavaTypeAdapter(MoneyAdapter.class)
        private Money total;

        public Invoice() {
        }

        private Invoice(String number, Money total) {
            this.number = number;
            this.total = total;
        }
    }
}
