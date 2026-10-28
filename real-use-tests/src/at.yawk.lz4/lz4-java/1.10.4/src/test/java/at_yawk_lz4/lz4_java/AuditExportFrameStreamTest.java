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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE.SIZE_64KB;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.BLOCK_CHECKSUM;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE;
import static net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits.CONTENT_CHECKSUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises LZ4 frame streams by archiving and restoring a warehouse audit export.
/// The export spans multiple blocks and carries block and content checksums for transport integrity.
public class AuditExportFrameStreamTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void restoresEveryRecordFromCompressedAuditExport() throws IOException {
        Path archive = directory.resolve("warehouse-audit.csv.lz4");
        String header = "event_id,warehouse,item,action,quantity";
        int recordCount = 4000;
        long uncompressedBytes = header.getBytes(UTF_8).length + 1;

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new LZ4FrameOutputStream(Files.newOutputStream(archive), SIZE_64KB,
                        BLOCK_INDEPENDENCE, BLOCK_CHECKSUM, CONTENT_CHECKSUM), UTF_8))) {
            writer.write(header);
            writer.write('\n');
            for (int eventId = 0; eventId < recordCount; eventId++) {
                String record = auditRecord(eventId);
                writer.write(record);
                writer.write('\n');
                uncompressedBytes += record.getBytes(UTF_8).length + 1;
            }
        }

        assertTrue(Files.size(archive) < uncompressedBytes,
                "The archived export should take less storage than its CSV source");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new LZ4FrameInputStream(Files.newInputStream(archive)), UTF_8))) {
            assertEquals(header, reader.readLine());
            for (int eventId = 0; eventId < recordCount; eventId++) {
                assertEquals(auditRecord(eventId), reader.readLine(), "Audit event " + eventId);
            }
            assertNull(reader.readLine(), "The restored export must contain no extra records");
        }
    }

    private static String auditRecord(int eventId) {
        return eventId + ",warehouse-east,item-0042,RESERVED,3";
    }
}
