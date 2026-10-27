/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises LZ4 single-frame decoding for a compressed attachment inside a binary parcel message.
/// The self-delimiting frame must leave the following delivery metadata available to the message reader.
public class ParcelAttachmentBoundaryTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void restoresPackingListWithoutConsumingTrailingDeliveryInstructions() throws IOException {
        byte[] packingList = ("sku,description,quantity\n"
                + "BOX-42,Reusable warehouse storage box,12\n".repeat(800)).getBytes(UTF_8);
        ByteArrayOutputStream encodedAttachment = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream compressor = new LZ4FrameOutputStream(encodedAttachment)) {
            compressor.write(packingList);
        }
        assertTrue(encodedAttachment.size() < packingList.length,
                "The parcel attachment should reduce the message's transport size");

        Path message = directory.resolve("parcel.message");
        try (DataOutputStream sender = new DataOutputStream(Files.newOutputStream(message))) {
            sender.writeUTF("parcel-742");
            sender.writeUTF("packing-list.csv");
            // The frame supplies its own boundary; the envelope needs no compressed-length field.
            encodedAttachment.writeTo(sender);
            sender.writeUTF("Dock 7: signature required");
            sender.writeInt(12);
            sender.writeUTF("END-PARCEL");
        }

        Path restoredAttachment = directory.resolve("packing-list.csv");
        try (DataInputStream receiver = new DataInputStream(Files.newInputStream(message))) {
            assertEquals("parcel-742", receiver.readUTF());
            assertEquals("packing-list.csv", receiver.readUTF());
            // Ordinary concatenated-frame mode would interpret the trailer as another frame header.
            try (LZ4FrameInputStream attachment = new LZ4FrameInputStream(receiver, true)) {
                Files.write(restoredAttachment, attachment.readAllBytes());
                assertArrayEquals(packingList, Files.readAllBytes(restoredAttachment),
                        "The receiving warehouse must recover the complete packing list");
                // The decoder remains open until the enclosing message has been consumed.
                assertEquals("Dock 7: signature required", receiver.readUTF());
                assertEquals(12, receiver.readInt(), "The delivery's declared carton count must survive");
                assertEquals("END-PARCEL", receiver.readUTF());
                assertEquals(-1, receiver.read(), "No part of the message should remain unread");
            }
        }
    }
}
