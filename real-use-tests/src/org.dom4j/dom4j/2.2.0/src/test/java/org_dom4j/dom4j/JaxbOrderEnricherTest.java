/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import jakarta.xml.bind.Element;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.dom4j.Document;
import org.dom4j.jaxb.JAXBObjectHandler;
import org.dom4j.jaxb.JAXBReader;
import org.dom4j.jaxb.JAXBWriter;
import org.dom4j.io.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises dom4j's JAXB binding subsystem to enrich an order in an application XML pipeline.
@XmlRootElement(name = "order", namespace = "urn:orders")
@XmlAccessorType(XmlAccessType.FIELD)
public class JaxbOrderEnricherTest implements Element {
    @XmlAttribute
    private String id;

    @XmlElement(namespace = "urn:orders")
    private String customer;

    @XmlElement(namespace = "urn:orders")
    private BigDecimal total;

    @Test
    @Timeout(60)
    public void unmarshalsOrderAndMarshalsEnrichedOrder() throws Exception {
        String orderXml = """
                <order xmlns="urn:orders" id="ORD-42">
                  <customer>Acme Bakery</customer>
                  <total>42.50</total>
                </order>
                """;
        List<Element> orders = new ArrayList<>();
        JAXBReader reader = new JAXBReader("org_dom4j.dom4j");
        JAXBObjectHandler handler = orders::add;
        reader.addObjectHandler("/order", handler);

        Document document = reader.read(new StringReader(orderXml));

        assertNotNull(document.getRootElement());
        JaxbOrderEnricherTest order = (JaxbOrderEnricherTest) orders.getFirst();
        assertEquals("ORD-42", order.id);
        assertEquals("Acme Bakery", order.customer);
        assertEquals(0, new BigDecimal("42.50").compareTo(order.total));

        order.customer = "Acme Bakery Wholesale";
        StringWriter output = new StringWriter();
        JAXBWriter writer = new JAXBWriter("org_dom4j.dom4j", OutputFormat.createCompactFormat());
        writer.setOutput(output);
        writer.write(order);

        String enrichedXml = output.toString();
        assertTrue(enrichedXml.contains("id=\"ORD-42\""));
        assertTrue(enrichedXml.contains("customer"));
        assertTrue(enrichedXml.contains("Acme Bakery Wholesale"));
        assertTrue(enrichedXml.contains("total"));
        assertTrue(enrichedXml.contains("42.50"));
    }
}
