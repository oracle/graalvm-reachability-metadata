/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Node;
import org.xmlunit.builder.Input;
import org.xmlunit.xpath.JAXPXPathEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises XMLUnit's XPath subsystem to query namespaced invoice data for order processing.
public class InvoiceXPathQueryTest {
    @Test
    @Timeout(60)
    public void extractsInvoiceLinesAndTotalForOrderProcessing() {
        Source invoice = Input.fromString("""
                <invoice xmlns="urn:acme:invoice">
                    <customerId>customer-42</customerId>
                    <line sku="A-100"><description>Keyboard</description><quantity>2</quantity><unitPrice>49.99</unitPrice></line>
                    <line sku="B-200"><description>Mouse</description><quantity>1</quantity><unitPrice>19.99</unitPrice></line>
                </invoice>
                """).build();
        JAXPXPathEngine xpath = new JAXPXPathEngine();
        xpath.setNamespaceContext(Map.of("inv", "urn:acme:invoice"));

        assertEquals("customer-42", xpath.evaluate(
                "/inv:invoice/inv:customerId", invoice));
        List<Node> lines = nodes(xpath.selectNodes("/inv:invoice/inv:line", invoice));
        assertEquals(2, lines.size());
        BigDecimal firstLineTotal = lineTotal(xpath, lines.get(0));
        BigDecimal secondLineTotal = lineTotal(xpath, lines.get(1));
        assertEquals(new BigDecimal("119.97"), firstLineTotal.add(secondLineTotal));

        List<Node> priorityLines = nodes(xpath.selectNodes(
                "/inv:invoice/inv:line[@sku='A-100']", invoice));
        assertEquals(1, priorityLines.size());
        assertEquals("Keyboard", xpath.evaluate("string(inv:description)", priorityLines.get(0)));
        assertEquals("2", xpath.evaluate("string(inv:quantity)", priorityLines.get(0)));
        assertEquals(new BigDecimal("99.98"), firstLineTotal);
    }

    private static BigDecimal lineTotal(JAXPXPathEngine xpath, Node line) {
        return new BigDecimal(xpath.evaluate(
                "number(inv:quantity) * number(inv:unitPrice)", line));
    }

    private static List<Node> nodes(Iterable<Node> selectedNodes) {
        List<Node> result = new ArrayList<>();
        for (Node selectedNode : selectedNodes) {
            result.add(selectedNode);
        }
        return result;
    }
}
