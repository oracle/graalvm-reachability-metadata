/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises direct-ByteBuffer LZ4 compression for a length-prefixed replication spool.
/// A receiver restores ordered batches from an NIO spool without copying compressed payloads to arrays.
public class DirectBufferReplicationSpoolTest {
    private static final int HEADER_BYTES = 3 * Integer.BYTES;

    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void replaysLengthPrefixedReplicationBatchesFromDirectBuffers() throws IOException {
        LZ4Factory factory = LZ4Factory.fastestInstance();
        LZ4Compressor compressor = factory.fastCompressor();
        LZ4FastDecompressor decompressor = factory.fastDecompressor();
        List<String> batches = List.of(batch(0), batch(1), batch(2));
        int capacity = 0;
        int originalBytes = 0;
        for (String batch : batches) {
            int length = batch.getBytes(UTF_8).length;
            capacity += HEADER_BYTES + compressor.maxCompressedLength(length);
            originalBytes += length;
        }

        ByteBuffer outbound = ByteBuffer.allocateDirect(capacity);
        for (int sequence = 0; sequence < batches.size(); sequence++) {
            byte[] encoded = batches.get(sequence).getBytes(UTF_8);
            ByteBuffer source = ByteBuffer.allocateDirect(encoded.length);
            source.put(encoded).flip();
            int payloadOffset = outbound.position() + HEADER_BYTES;
            int compressedLength = compressor.compress(source, 0, source.remaining(),
                    outbound, payloadOffset, outbound.capacity() - payloadOffset);
            outbound.putInt(sequence).putInt(encoded.length).putInt(compressedLength);
            outbound.position(payloadOffset + compressedLength);
        }
        outbound.flip();

        Path spool = directory.resolve("replication.spool");
        try (FileChannel output = FileChannel.open(spool, CREATE_NEW, WRITE)) {
            while (outbound.hasRemaining()) {
                output.write(outbound);
            }
        }
        assertTrue(Files.size(spool) < originalBytes,
                "The replication spool should reduce storage for repetitive change records");

        ByteBuffer inbound = ByteBuffer.allocateDirect(Math.toIntExact(Files.size(spool)));
        try (FileChannel input = FileChannel.open(spool, READ)) {
            while (inbound.hasRemaining()) {
                assertTrue(input.read(inbound) > 0, "The spool must contain every declared byte");
            }
        }
        inbound.flip();

        for (int sequence = 0; sequence < batches.size(); sequence++) {
            assertEquals(sequence, inbound.getInt(), "Replication order must survive storage");
            int restoredLength = inbound.getInt();
            int compressedLength = inbound.getInt();
            ByteBuffer compressed = inbound.slice();
            compressed.limit(compressedLength);
            ByteBuffer restored = ByteBuffer.allocateDirect(restoredLength);
            int consumed = decompressor.decompress(compressed, 0, restored, 0, restoredLength);
            assertEquals(compressedLength, consumed, "The decoder must consume exactly one batch");
            assertEquals(batches.get(sequence), UTF_8.decode(restored).toString(),
                    "Every change record must arrive intact");
            inbound.position(inbound.position() + compressedLength);
        }
        assertEquals(0, inbound.remaining(), "No bytes may be lost between replication batches");
    }

    private static String batch(int sequence) {
        StringBuilder records = new StringBuilder("change_id,table,operation,key,value\n");
        for (int change = 0; change < 1000; change++) {
            records.append(sequence * 1000 + change)
                    .append(",inventory,UPSERT,warehouse-east-item-").append(change)
                    .append(",available=42;reserved=3\n");
        }
        return records.toString();
    }
}
