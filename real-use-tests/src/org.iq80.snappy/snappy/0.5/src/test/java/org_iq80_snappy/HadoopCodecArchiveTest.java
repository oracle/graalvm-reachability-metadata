/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy;

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
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Snappy's Hadoop codec integration for an application archive.
public class HadoopCodecArchiveTest {
    @Test
    @Timeout(60)
    public void roundTripsAnArchiveThroughTheHadoopCompressionCodec() throws IOException {
        byte[] archive = createArchive();
        HadoopSnappyCodec codec = new HadoopSnappyCodec();

        ByteArrayOutputStream compressedArchive = new ByteArrayOutputStream();
        try (CompressionOutputStream writer = codec.createOutputStream(compressedArchive)) {
            writer.write(archive);
            writer.finish();
        }

        byte[] restoredArchive = readArchive(codec, compressedArchive.toByteArray());

        assertEquals(".snappy", codec.getDefaultExtension());
        assertArrayEquals(archive, restoredArchive);
    }

    private static byte[] readArchive(HadoopSnappyCodec codec, byte[] compressedArchive)
            throws IOException {
        ByteArrayOutputStream restoredArchive = new ByteArrayOutputStream();
        try (CompressionInputStream reader = codec.createInputStream(
                new ByteArrayInputStream(compressedArchive))) {
            byte[] buffer = new byte[257];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                restoredArchive.write(buffer, 0, bytesRead);
            }
        }
        return restoredArchive.toByteArray();
    }

    private static byte[] createArchive() {
        StringBuilder archive = new StringBuilder();
        for (int record = 0; record < 2048; record++) {
            archive.append("event=").append(record)
                    .append("|tenant=warehouse-denver|type=inventory-adjustment")
                    .append("|status=accepted|actor=fulfillment-service\n");
        }
        return archive.toString().getBytes(StandardCharsets.UTF_8);
    }
}
