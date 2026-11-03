/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's byte input subsystem to compare carrier messages received as application payloads.
public class CarrierMessageInputTest {
    @Test
    @Timeout(60)
    public void comparesMessagesFromBytePayloadsAndDetectsChangedQuantities() {
        String expectedMessage = """
                <shipment>
                    <shipmentId>SHIP-2048</shipmentId>
                    <parcel trackingId="BOX-1"><quantity>2</quantity></parcel>
                </shipment>
                """;
        String receivedMessage = """
                <shipment>
                    <shipmentId>SHIP-2048</shipmentId>
                    <parcel trackingId="BOX-1"><quantity>2</quantity></parcel>
                </shipment>
                """;

        Diff matchingMessages = compareMessages(expectedMessage, receivedMessage);

        assertFalse(matchingMessages.hasDifferences(), matchingMessages::fullDescription);

        Diff changedMessage = compareMessages(expectedMessage, receivedMessage.replace(
                "<quantity>2</quantity>", "<quantity>3</quantity>"));

        assertTrue(changedMessage.hasDifferences());
        assertTrue(changedMessage.fullDescription().contains("quantity"),
                changedMessage::fullDescription);
    }

    private static Diff compareMessages(String expectedMessage, String receivedMessage) {
        return DiffBuilder.compare(Input.fromByteArray(
                        expectedMessage.getBytes(StandardCharsets.UTF_8)).build())
                .withTest(Input.fromStream(new ByteArrayInputStream(
                        receivedMessage.getBytes(StandardCharsets.UTF_8))).build())
                .checkForIdentical()
                .build();
    }
}
