/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the XXHash32 Checksum adapter to transfer binary job checkpoints through checked streams.
/// A worker validates each checkpoint's transfer checksum before using its restored partition offsets.
public class JobCheckpointChecksumAdapterTest {
    @Test
    @Timeout(60)
    public void restoresSuccessiveCheckpointsWithIndependentTransferChecksums() throws IOException {
        XXHashFactory hashes = XXHashFactory.fastestInstance();
        List<List<PartitionOffset>> checkpoints = List.of(
                List.of(new PartitionOffset("orders-east", 0, 18000L),
                        new PartitionOffset("orders-east", 1, 22400L)),
                List.of(new PartitionOffset("orders-east", 0, 18500L),
                        new PartitionOffset("orders-east", 1, 23100L),
                        new PartitionOffset("orders-west", 0, 900L)));

        try (StreamingXXHash32 senderHash = hashes.newStreamingHash32(0);
                StreamingXXHash32 receiverHash = hashes.newStreamingHash32(0)) {
            Checksum senderChecksum = senderHash.asChecksum();
            Checksum receiverChecksum = receiverHash.asChecksum();
            for (int generation = 0; generation < checkpoints.size(); generation++) {
                // Reuse a transfer session, but checksum each checkpoint independently.
                senderChecksum.reset();
                receiverChecksum.reset();
                List<PartitionOffset> expected = checkpoints.get(generation);
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                try (DataOutputStream output = new DataOutputStream(
                        new CheckedOutputStream(payload, senderChecksum))) {
                    output.writeByte(1);
                    output.writeLong(generation);
                    output.writeInt(expected.size());
                    for (PartitionOffset offset : expected) {
                        output.writeUTF(offset.topic());
                        output.writeInt(offset.partition());
                        output.writeLong(offset.nextOffset());
                    }
                }
                long transferChecksum = senderChecksum.getValue();

                List<PartitionOffset> restored = new ArrayList<>();
                try (DataInputStream input = new DataInputStream(new CheckedInputStream(
                        new ByteArrayInputStream(payload.toByteArray()), receiverChecksum))) {
                    assertEquals(1, input.readUnsignedByte(), "Checkpoint format");
                    assertEquals(generation, input.readLong(), "Checkpoint generation");
                    int partitions = input.readInt();
                    assertEquals(expected.size(), partitions);
                    for (int partition = 0; partition < partitions; partition++) {
                        restored.add(new PartitionOffset(input.readUTF(), input.readInt(), input.readLong()));
                    }
                    assertEquals(-1, input.read(), "The complete checkpoint must be consumed");
                }

                assertEquals(transferChecksum, receiverChecksum.getValue(),
                        "The received checkpoint must match its transfer checksum before resuming the job");
                assertEquals(expected, restored, "The worker must resume every partition at its saved offset");
            }
        }
    }

    private record PartitionOffset(String topic, int partition, long nextOffset) {
    }
}
