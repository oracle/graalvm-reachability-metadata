/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.BuildingStreamWriter;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises Saxon's XDM tree-building and axis-navigation subsystem for a shipment manifest.
 *
 * A fulfillment service builds a namespaced manifest from records received from its queue, then
 * walks the resulting XDM tree to create a dispatch summary. The summary uses public node names,
 * attributes, and descendant values just as an application component would.
 */
public class ShipmentManifestTreeTest {

    private static final String MANIFEST_NAMESPACE = "urn:fulfillment:manifest";
    private static final QName ORDER = new QName("m", MANIFEST_NAMESPACE, "order");
    private static final QName LINE = new QName("m", MANIFEST_NAMESPACE, "line");
    private static final QName ORDER_ID = new QName("orderId");
    private static final QName CUSTOMER = new QName("customer");
    private static final QName SKU = new QName("sku");
    private static final QName QUANTITY = new QName("quantity");

    @Test
    @Timeout(60)
    public void buildsAndNavigatesManifestForDispatchSummary() throws Exception {
        Processor processor = new Processor(false);
        XdmNode manifest = buildManifest(processor);
        XdmNode manifestElement = manifest.children().iterator().next();

        List<String> dispatches = new ArrayList<>();
        for (var orderIterator = manifestElement.axisIterator(Axis.CHILD, ORDER);
                orderIterator.hasNext();) {
            XdmNode order = orderIterator.next();
            List<String> lines = new ArrayList<>();
            for (var lineIterator = order.axisIterator(Axis.DESCENDANT, LINE);
                    lineIterator.hasNext();) {
                XdmNode line = lineIterator.next();
                lines.add(line.getAttributeValue(SKU) + "x" + line.getAttributeValue(QUANTITY));
            }
            dispatches.add(order.getAttributeValue(ORDER_ID)
                    + "|" + order.getAttributeValue(CUSTOMER) + "|" + String.join(",", lines));
        }

        assertNotNull(manifestElement.getNodeName());
        assertEquals("m", manifestElement.getNodeName().getPrefix());
        assertEquals(List.of(
                "A-17|acme|PAPERx4,PENSx10",
                "B-04|bravo|MUGx3"), dispatches);
    }

    private static XdmNode buildManifest(Processor processor) throws Exception {
        BuildingStreamWriter writer = processor.newDocumentBuilder().newBuildingStreamWriter();
        writer.writeStartDocument();
        writer.writeStartElement("m", "manifest", MANIFEST_NAMESPACE);
        writer.writeNamespace("m", MANIFEST_NAMESPACE);
        writeOrder(writer, "A-17", "acme", new String[][]{
                {"PAPER", "4"}, {"PENS", "10"}});
        writeOrder(writer, "B-04", "bravo", new String[][]{{"MUG", "3"}});
        writer.writeEndElement();
        writer.writeEndDocument();
        return writer.getDocumentNode();
    }

    private static void writeOrder(
            BuildingStreamWriter writer,
            String orderId,
            String customer,
            String[][] lines) throws Exception {
        writer.writeStartElement("m", "order", MANIFEST_NAMESPACE);
        writer.writeAttribute("orderId", orderId);
        writer.writeAttribute("customer", customer);
        for (String[] line : lines) {
            writer.writeStartElement("m", "line", MANIFEST_NAMESPACE);
            writer.writeAttribute("sku", line[0]);
            writer.writeAttribute("quantity", line[1]);
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }
}
