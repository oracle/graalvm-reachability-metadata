/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.NodeFilters;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's node and attribute filtering subsystem to compare shipment records while ignoring transport metadata.
public class ShipmentMetadataFilteringTest {
    @Test
    @Timeout(60)
    public void ignoresTransportMetadataButDetectsShipmentChanges() {
        String expectedShipment = """
                <shipment traceId="trace-expected">
                    <shipmentId>SHIP-2048</shipmentId>
                    <generatedAt>2025-01-01T10:00:00Z</generatedAt>
                    <parcel trackingId="BOX-1"><weight unit="kg">2.5</weight></parcel>
                </shipment>
                """;
        String receivedShipment = """
                <shipment traceId="trace-received">
                    <shipmentId>SHIP-2048</shipmentId>
                    <generatedAt>2025-01-01T10:05:00Z</generatedAt>
                    <parcel trackingId="BOX-1"><weight unit="kg">2.5</weight></parcel>
                </shipment>
                """;

        Diff metadataOnlyDifference = compareShipments(expectedShipment, receivedShipment);

        assertFalse(metadataOnlyDifference.hasDifferences(), metadataOnlyDifference::fullDescription);

        Diff changedShipment = compareShipments(expectedShipment, receivedShipment.replace(
                "<weight unit=\"kg\">2.5</weight>", "<weight unit=\"kg\">3.0</weight>"));

        assertTrue(changedShipment.hasDifferences());
    }

    private static Diff compareShipments(String expectedShipment, String receivedShipment) {
        return DiffBuilder.compare(Input.fromString(expectedShipment))
                .withTest(Input.fromString(receivedShipment))
                .withNodeFilter(NodeFilters.satifiesAll(NodeFilters.Default,
                        node -> !isGeneratedAtNode(node)))
                .withAttributeFilter(attribute -> !isTraceIdAttribute(attribute))
                .checkForIdentical()
                .build();
    }

    private static boolean isGeneratedAtNode(Node node) {
        return node.getNodeType() == Node.ELEMENT_NODE
                && "generatedAt".equals(node.getNodeName());
    }

    private static boolean isTraceIdAttribute(Attr attribute) {
        return "traceId".equals(attribute.getName());
    }
}
