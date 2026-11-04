/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.iq80.snappy.HadoopSnappyCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Snappy's Hadoop block-stream reset subsystem for an application that rolls output segments.
public class HadoopBlockStreamResetTest {
    @Test
    @Timeout(60)
    public void continuesWritingAfterFinishingARecordBatch() throws IOException {
        byte[] firstBatch = createBatch("morning", 700);
        byte[] secondBatch = createBatch("evening", 900);
        byte[] expected = concatenate(firstBatch, secondBatch);
        HadoopSnappyCodec codec = configuredCodec();
        ByteArrayOutputStream storedData = new ByteArrayOutputStream();

        try (CompressionOutputStream writer = codec.createOutputStream(storedData)) {
            writer.write(firstBatch);
            writer.resetState();
            writer.write(secondBatch);
            writer.finish();
        }

        assertTrue(storedData.size() > 0);
        assertArrayEquals(expected, readAll(codec, storedData.toByteArray()));
    }

    private static HadoopSnappyCodec configuredCodec() {
        Configuration configuration = new Configuration(false);
        configuration.setInt("io.compression.codec.snappy.buffersize", 256);
        HadoopSnappyCodec codec = new HadoopSnappyCodec();
        codec.setConf(configuration);
        return codec;
    }

    private static byte[] readAll(HadoopSnappyCodec codec, byte[] storedData) throws IOException {
        ByteArrayOutputStream restoredData = new ByteArrayOutputStream();
        try (CompressionInputStream reader = codec.createInputStream(
                new ByteArrayInputStream(storedData))) {
            byte[] buffer = new byte[73];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                restoredData.write(buffer, 0, bytesRead);
            }
        }
        return restoredData.toByteArray();
    }

    private static byte[] createBatch(String shift, int recordCount) {
        StringBuilder batch = new StringBuilder();
        for (int record = 0; record < recordCount; record++) {
            batch.append("batch=").append(shift)
                    .append("|record=").append(record)
                    .append("|service=inventory|operation=reconcile|status=accepted\n");
        }
        return batch.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }
}
