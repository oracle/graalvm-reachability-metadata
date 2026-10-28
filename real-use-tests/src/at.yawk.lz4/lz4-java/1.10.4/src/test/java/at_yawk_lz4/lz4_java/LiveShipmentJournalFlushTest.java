/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises LZ4 synchronous block flushing for a live shipment-event journal reader.
/// A dashboard consumes each published event while the producer keeps the compressed journal open.
public class LiveShipmentJournalFlushTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void updatesShipmentDashboardBeforeTheJournalIsClosed() throws IOException {
        Path journal = directory.resolve("live-shipments.lz4block");
        List<ShipmentEvent> events = List.of(
                new ShipmentEvent(1, "shipment-42", "PACKED"),
                new ShipmentEvent(2, "shipment-99", "PACKED"),
                new ShipmentEvent(3, "shipment-42", "DISPATCHED"),
                new ShipmentEvent(4, "shipment-42", "DELIVERED"));
        Map<String, String> dashboard = new HashMap<>();
        LZ4Factory codecs = LZ4Factory.fastestInstance();

        // Both ends of this application journal agree on CRC32 block integrity checks.
        // Events are much smaller than a block, so publication must flush pending compressed data.
        try (LZ4BlockOutputStream compressed = new LZ4BlockOutputStream(
                Files.newOutputStream(journal), 65536, codecs.fastCompressor(), new CRC32(), true);
                DataOutputStream producer = new DataOutputStream(compressed);
                DataInputStream follower = new DataInputStream(new LZ4BlockInputStream(
                        Files.newInputStream(journal), codecs.fastDecompressor(), new CRC32()))) {
            for (ShipmentEvent event : events) {
                event.writeTo(producer);
                producer.flush();

                // Read only the published record: probing EOF would terminate a live tail too early.
                ShipmentEvent observed = ShipmentEvent.readFrom(follower);
                assertEquals(event, observed, "A flush must publish the complete next event without closing");
                dashboard.put(observed.shipment(), observed.status());
                assertEquals(event.status(), dashboard.get(event.shipment()));
            }
            assertEquals(Map.of("shipment-42", "DELIVERED", "shipment-99", "PACKED"), dashboard,
                    "The dashboard must reflect the complete ordered event history before producer shutdown");

            compressed.finish();
            assertEquals(-1, follower.read(), "Only a finished journal signals the end of the event feed");
        }
    }

    private record ShipmentEvent(long sequence, String shipment, String status) {
        private void writeTo(DataOutputStream output) throws IOException {
            output.writeLong(sequence);
            output.writeUTF(shipment);
            output.writeUTF(status);
        }

        private static ShipmentEvent readFrom(DataInputStream input) throws IOException {
            return new ShipmentEvent(input.readLong(), input.readUTF(), input.readUTF());
        }
    }
}
