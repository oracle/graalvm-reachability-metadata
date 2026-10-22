/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the GZIP compression subsystem by appending and replaying compressed audit-log batches.
 */
public class GzipAuditLogTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void replaysConcatenatedBatchesWithoutLosingEvents() throws IOException {
        List<String> batches = List.of(
                """
                2024-01-01T00:00:00Z,alice,order-created,order-101
                2024-01-01T00:01:00Z,bob,payment-approved,order-101
                """,
                """
                2024-01-01T00:02:00Z,alice,order-created,order-102
                2024-01-01T00:03:00Z,bob,payment-declined,order-102
                """);
        long exportTime = 1704067200000L;
        Path auditLog = Files.createFile(temporaryDirectory.resolve("audit.csv.gz"));

        // Each completed member is independently compressed, as in an append-only log exporter.
        for (int batch = 0; batch < batches.size(); batch++) {
            GzipParameters parameters = new GzipParameters();
            parameters.setCompressionLevel(Deflater.BEST_COMPRESSION);
            parameters.setFilename("audit-batch-" + batch + ".csv");
            parameters.setComment("Order service audit export");
            parameters.setModificationTime(exportTime);
            try (GzipCompressorOutputStream output = new GzipCompressorOutputStream(
                    Files.newOutputStream(auditLog, StandardOpenOption.APPEND), parameters)) {
                output.write(batches.get(batch).getBytes(StandardCharsets.UTF_8));
            }
        }

        Path replay = temporaryDirectory.resolve("audit.csv");
        try (GzipCompressorInputStream input = new GzipCompressorInputStream(Files.newInputStream(auditLog), true)) {
            GzipParameters header = input.getMetaData();
            assertEquals("audit-batch-0.csv", header.getFilename());
            assertEquals("Order service audit export", header.getComment());
            assertEquals(exportTime, header.getModificationTime());
            Files.copy(input, replay);
            assertEquals(-1, input.read());
        }

        assertEquals(String.join("", batches), Files.readString(replay, StandardCharsets.UTF_8));
        assertEquals(List.of(
                "2024-01-01T00:00:00Z,alice,order-created,order-101",
                "2024-01-01T00:01:00Z,bob,payment-approved,order-101",
                "2024-01-01T00:02:00Z,alice,order-created,order-102",
                "2024-01-01T00:03:00Z,bob,payment-declined,order-102"),
                Files.readAllLines(replay, StandardCharsets.UTF_8));
    }
}
