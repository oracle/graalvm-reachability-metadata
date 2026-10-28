/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream.BlockSize;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the framed LZ4 compression subsystem by transferring a binary sensor-telemetry batch for aggregation.
 */
public class Lz4TelemetryBatchTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(60)
    public void aggregatesEverySensorReadingAcrossCompressedBlocks() throws IOException {
        Path batch = temporaryDirectory.resolve("warehouse-telemetry.lz4");
        int sampleCount = 3000;
        int sensorCount = 4;
        long startTime = 1704067200000L;

        // Small blocks keep the edge gateway's compression buffer bounded.
        // This batch spans multiple 64 KiB blocks within a single frame.
        try (FramedLZ4CompressorOutputStream compressed = new FramedLZ4CompressorOutputStream(
                Files.newOutputStream(batch), new Parameters(BlockSize.K64));
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(compressed))) {
            output.writeUTF("warehouse-north/temperature-v1");
            output.writeInt(sampleCount * sensorCount);
            for (int sample = 0; sample < sampleCount; sample++) {
                for (int sensor = 0; sensor < sensorCount; sensor++) {
                    output.writeLong(startTime + sample * 1000L);
                    output.writeInt(sensor);
                    output.writeInt(20000 + sensor * 100 + sample % 10);
                }
            }
        }

        Map<Integer, Integer> readingsPerSensor = new HashMap<>();
        Map<Integer, Long> temperatureTotals = new HashMap<>();
        try (FramedLZ4CompressorInputStream decompressed = new FramedLZ4CompressorInputStream(
                new BufferedInputStream(Files.newInputStream(batch)));
                DataInputStream input = new DataInputStream(new BufferedInputStream(decompressed))) {
            assertEquals("warehouse-north/temperature-v1", input.readUTF());
            int readingCount = input.readInt();
            assertEquals(12000, readingCount);
            for (int reading = 0; reading < readingCount; reading++) {
                long timestamp = input.readLong();
                int sensor = input.readInt();
                int milliDegrees = input.readInt();
                assertEquals(startTime + (reading / sensorCount) * 1000L, timestamp);
                assertEquals(reading % sensorCount, sensor);
                assertEquals(20000 + sensor * 100 + (reading / sensorCount) % 10, milliDegrees);
                readingsPerSensor.merge(sensor, 1, Integer::sum);
                temperatureTotals.merge(sensor, (long) milliDegrees, Long::sum);
            }
            // Reading to EOF validates the frame's content checksum as well as its final block.
            assertEquals(-1, input.read());
        }

        assertEquals(Map.of(0, 3000, 1, 3000, 2, 3000, 3, 3000), readingsPerSensor);
        assertEquals(Map.of(0, 60013500L, 1, 60313500L, 2, 60613500L, 3, 60913500L), temperatureTotals);
    }
}
