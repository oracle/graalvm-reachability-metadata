/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SAXDestination;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's SAX destination subsystem to deliver an XML shipment manifest to an application
 * event handler.
 *
 * A fulfillment service receives an XDM document from a Saxon pipeline and sends it to an existing
 * SAX-based dispatch component. The component records shipment events from public SAX callbacks,
 * just as an application would before handing them to its queue.
 */
public class SaxDestinationShipmentTest {

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
    public void sendsShipmentDocumentToSaxDispatchHandler() throws Exception {
        Processor processor = new Processor(false);
        XdmNode shipment = processor.newDocumentBuilder().build(
                new StreamSource(new StringReader(SHIPMENT)));
        DispatchHandler dispatchHandler = new DispatchHandler();

        processor.writeXdmValue(shipment, new SAXDestination(dispatchHandler));

        assertEquals(List.of(
                "A-17|acme|PAPERx4,PENSx10",
                "B-04|bravo|MUGx3"), dispatchHandler.orders);
    }

    private static final class DispatchHandler extends DefaultHandler {

        private final List<String> orders = new ArrayList<>();
        private String orderId;
        private String customer;
        private final List<String> lines = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if ("order".equals(localName)) {
                orderId = attributes.getValue("id");
                customer = attributes.getValue("customer");
                lines.clear();
            } else if ("line".equals(localName)) {
                lines.add(attributes.getValue("sku") + "x" + attributes.getValue("quantity"));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("order".equals(localName)) {
                orders.add(orderId + "|" + customer + "|" + String.join(",", lines));
            }
        }
    }
}
