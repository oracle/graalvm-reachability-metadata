/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's DTD validation subsystem to accept a partner shipment feed.
 *
 * A warehouse service validates the feed against the partner contract before handing it to a
 * dispatch component. The component reads validated order and line attributes through the same
 * XDM tree API it would use for a normal application document.
 */
public class ShipmentProjectionTest {

    private static final String SHIPMENTS = """
            <!DOCTYPE shipments [
              <!ELEMENT shipments (order+)>
              <!ELEMENT order (customer,line+)>
              <!ATTLIST order id ID #REQUIRED priority (urgent|normal) #REQUIRED>
              <!ELEMENT customer (#PCDATA)>
              <!ELEMENT line EMPTY>
              <!ATTLIST line sku CDATA #REQUIRED status (ready|backorder) #REQUIRED
                    quantity CDATA #REQUIRED warehouse CDATA "north">
            ]>
            <shipments>
              <order id="A-17" priority="urgent">
                <customer>acme</customer>
                <line sku="PAPER" status="ready" quantity="4"/>
                <line sku="PENS" status="backorder" quantity="10" warehouse="south"/>
              </order>
              <order id="B-04" priority="urgent">
                <customer>charlie</customer>
                <line sku="MUG" status="ready" quantity="3"/>
              </order>
            </shipments>
            """;

    @Test
    @Timeout(60)
    public void validatesPartnerFeedAndReadsDefaultWarehouse() throws Exception {
        Processor processor = new Processor(false);
        DocumentBuilder documentBuilder = processor.newDocumentBuilder();
        documentBuilder.setDTDValidation(true);

        XdmNode shipments = documentBuilder.build(
                new StreamSource(new StringReader(SHIPMENTS)));

        List<String> dispatches = new ArrayList<>();
        for (var lines = shipments.axisIterator(Axis.DESCENDANT, new QName("line"));
                lines.hasNext();) {
            XdmNode line = lines.next();
            XdmNode order = line.getParent();
            dispatches.add(order.attribute("id") + ":" + line.attribute("sku")
                    + "@" + line.attribute("warehouse"));
        }

        assertEquals(List.of("A-17:PAPER@north", "A-17:PENS@south", "B-04:MUG@north"),
                dispatches);
    }
}
