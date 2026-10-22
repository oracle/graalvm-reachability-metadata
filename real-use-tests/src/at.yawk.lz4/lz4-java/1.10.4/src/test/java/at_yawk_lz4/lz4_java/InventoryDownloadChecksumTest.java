/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/// Exercises streaming XXHash64 checksums for inventory downloads checked against a published manifest.
/// The checksum detects accidental transfer corruption; it is not used for authentication.
public class InventoryDownloadChecksumTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void acceptsCompleteDownloadAndRejectsSameLengthCorruption() throws IOException {
        XXHashFactory hashes = XXHashFactory.fastestInstance();
        StringBuilder export = new StringBuilder("sku,warehouse,available\n");
        for (int item = 0; item < 5000; item++) {
            export.append("SKU-").append(item).append(",east,").append(item % 100).append('\n');
        }
        byte[] publishedBytes = export.toString().getBytes(UTF_8);
        long publishedChecksum = hashes.hash64().hash(publishedBytes, 0, publishedBytes.length, 0);
        Path manifest = directory.resolve("inventory.xxhash64");
        Files.writeString(manifest, Long.toUnsignedString(publishedChecksum, 16) + "\n", UTF_8);
        Path download = directory.resolve("inventory.csv");
        Files.write(download, publishedBytes);

        long expectedChecksum = Long.parseUnsignedLong(Files.readString(manifest, UTF_8).trim(), 16);
        assertEquals(expectedChecksum, checksum(download, hashes),
                "A complete streamed download must match the publisher's checksum");

        // Simulate a transfer or storage error that cannot be detected by comparing file lengths.
        try (RandomAccessFile damagedDownload = new RandomAccessFile(download.toFile(), "rw")) {
            long offset = damagedDownload.length() / 2;
            damagedDownload.seek(offset);
            int original = damagedDownload.readUnsignedByte();
            damagedDownload.seek(offset);
            damagedDownload.writeByte(original ^ 1);
        }

        assertEquals(publishedBytes.length, Files.size(download));
        assertNotEquals(expectedChecksum, checksum(download, hashes),
                "A changed download must be rejected even when its length is unchanged");
    }

    private static long checksum(Path download, XXHashFactory hashes) throws IOException {
        try (InputStream input = Files.newInputStream(download);
                StreamingXXHash64 checksum = hashes.newStreamingHash64(0)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checksum.update(buffer, 0, count);
            }
            return checksum.getValue();
        }
    }
}
