/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXModifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises dom4j's SAX modifier subsystem to normalize orders in an application XML pipeline.
public class SaxModifierOrderNormalizerTest {
    @Test
    @Timeout(60)
    public void normalizesOrdersWhileReadingFeed() throws Exception {
        String feed = """
                <orders>
                  <order id="ORD-31" status="pending">
                    <customer>  Acme Bakery  </customer>
                    <line sku="BREAD" quantity="2"/>
                  </order>
                  <order id="ORD-32" status="pending">
                    <customer>Northwind Cafe</customer>
                    <line sku="COFFEE" quantity="1"/>
                  </order>
                </orders>
                """;

        SAXModifier modifier = new SAXModifier();
        modifier.addModifier("/orders/order", element -> {
            element.addAttribute("status", "ready");
            element.addAttribute("normalized", "true");
            Element customer = element.element("customer");
            customer.setText(customer.getTextTrim());
            element.addElement("fulfillment").addText("standard");
            return element;
        });

        Document document = modifier.modify(new StringReader(feed));
        assertNotNull(document.getRootElement());
        assertEquals(2, document.getRootElement().elements("order").size());

        Element firstOrder = document.getRootElement().element("order");
        assertEquals("ORD-31", firstOrder.attributeValue("id"));
        assertEquals("ready", firstOrder.attributeValue("status"));
        assertEquals("true", firstOrder.attributeValue("normalized"));
        assertEquals("Acme Bakery", firstOrder.elementText("customer"));
        assertEquals("standard", firstOrder.elementText("fulfillment"));
        assertEquals("BREAD", firstOrder.element("line").attributeValue("sku"));

        Element secondOrder = document.getRootElement().elements("order").get(1);
        assertEquals("ready", secondOrder.attributeValue("status"));
        assertEquals("Northwind Cafe", secondOrder.elementTextTrim("customer"));
        assertEquals("standard", secondOrder.elementTextTrim("fulfillment"));
    }
}
