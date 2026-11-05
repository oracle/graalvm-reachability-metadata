/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.BuildingContentHandler;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's SAX input subsystem to build an XDM shipment document from parser events.
 *
 * A warehouse service receives XML from an existing SAX-based ingestion pipeline. It gives Saxon's
 * building content handler to that pipeline, then reads the resulting XDM document to prepare
 * dispatch records just as an application would.
 */
public class SaxInputShipmentTest {

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
    public void buildsShipmentDocumentFromSaxParserEvents() throws Exception {
        Processor processor = new Processor(false);
        BuildingContentHandler contentHandler = processor.newDocumentBuilder()
                .newBuildingContentHandler();

        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(contentHandler);
        reader.parse(new InputSource(new StringReader(SHIPMENT)));

        XdmNode shipment = contentHandler.getDocumentNode();
        XdmNode shipmentElement = shipment.children("urn:dispatch", "shipment").iterator().next();
        List<String> dispatches = new ArrayList<>();
        for (XdmNode order : shipmentElement.children("urn:dispatch", "order")) {
            List<String> lines = new ArrayList<>();
            for (XdmNode line : order.children("urn:dispatch", "line")) {
                lines.add(line.attribute("sku") + "x" + line.attribute("quantity"));
            }
            dispatches.add(order.attribute("id") + "|" + order.attribute("customer") + "|"
                    + String.join(",", lines));
        }

        assertEquals(List.of(
                "A-17|acme|PAPERx4,PENSx10",
                "B-04|bravo|MUGx3"), dispatches);
    }
}
