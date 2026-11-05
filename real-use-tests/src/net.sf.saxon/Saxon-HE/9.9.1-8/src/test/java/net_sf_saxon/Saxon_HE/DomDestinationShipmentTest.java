/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's DOM destination subsystem to hand an XDM shipment document to an application
 * DOM consumer.
 *
 * A fulfillment service receives a document from a Saxon pipeline and sends it to an existing DOM-
 * based dispatch component. The component reads public DOM elements and attributes, just as an
 * application would before handing the shipment to its queue.
 */
public class DomDestinationShipmentTest {

    private static final String SHIPMENT = """
            <shipment xmlns="urn:dispatch">
              <order id="A-17" customer="acme">
                <line sku="PAPER" quantity="4"/>
                <line sku="PENS" quantity="10"/>
              </order>
              <order id="B-04" customer="bravo">
                <line sku="MUG" quantity="3"/>
              </order>
            </shipment>
            """;

    @Test
    @Timeout(60)
    public void sendsShipmentDocumentToDomDispatchComponent() throws Exception {
        Processor processor = new Processor(false);
        XdmNode shipment = processor.newDocumentBuilder().build(
                new StreamSource(new StringReader(SHIPMENT)));
        Document domDocument = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();

        processor.writeXdmValue(shipment, new DOMDestination(domDocument));

        Element shipmentElement = domDocument.getDocumentElement();
        NodeList orderNodes = shipmentElement.getElementsByTagNameNS("urn:dispatch", "order");
        List<String> dispatches = new ArrayList<>();
        for (int i = 0; i < orderNodes.getLength(); i++) {
            Element order = (Element) orderNodes.item(i);
            NodeList lineNodes = order.getElementsByTagNameNS("urn:dispatch", "line");
            List<String> lines = new ArrayList<>();
            for (int j = 0; j < lineNodes.getLength(); j++) {
                Element line = (Element) lineNodes.item(j);
                lines.add(line.getAttribute("sku") + "x" + line.getAttribute("quantity"));
            }
            dispatches.add(order.getAttribute("id") + "|" + order.getAttribute("customer") + "|"
                    + String.join(",", lines));
        }

        assertEquals("shipment", shipmentElement.getLocalName());
        assertEquals(List.of(
                "A-17|acme|PAPERx4,PENSx10",
                "B-04|bravo|MUGx3"), dispatches);
    }
}
