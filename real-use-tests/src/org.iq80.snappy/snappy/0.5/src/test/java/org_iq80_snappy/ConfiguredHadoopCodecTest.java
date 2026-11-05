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
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises Snappy's Hadoop codec configuration subsystem for an application with bounded buffers.
public class ConfiguredHadoopCodecTest {
    @Test
    @Timeout(60)
    public void roundTripsAnArchiveWithTheConfiguredBufferSize() throws IOException {
        HadoopSnappyCodec codec = configuredCodec();
        byte[] archive = createArchive();
        ByteArrayOutputStream compressedArchive = new ByteArrayOutputStream();

        try (CompressionOutputStream writer = codec.createOutputStream(compressedArchive)) {
            writer.write(archive);
            writer.finish();
        }

        byte[] restoredArchive;
        try (CompressionInputStream reader = codec.createInputStream(
                new ByteArrayInputStream(compressedArchive.toByteArray()))) {
            ByteArrayOutputStream restored = new ByteArrayOutputStream(archive.length);
            byte[] buffer = new byte[211];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                restored.write(buffer, 0, bytesRead);
            }
            restoredArchive = restored.toByteArray();
        }

        assertArrayEquals(archive, restoredArchive);
    }

    private static HadoopSnappyCodec configuredCodec() {
        Configuration configuration = new Configuration(false);
        configuration.setInt("io.compression.codec.snappy.buffersize", 1024);
        HadoopSnappyCodec codec = new HadoopSnappyCodec();
        codec.setConf(configuration);
        assertSame(configuration, codec.getConf());
        return codec;
    }

    private static byte[] createArchive() {
        StringBuilder archive = new StringBuilder();
        for (int record = 0; record < 4096; record++) {
            archive.append("event=").append(record)
                    .append("|tenant=warehouse-denver|type=inventory-adjustment")
                    .append("|status=accepted|actor=fulfillment-service\n");
        }
        return archive.toString().getBytes(StandardCharsets.UTF_8);
    }
}
