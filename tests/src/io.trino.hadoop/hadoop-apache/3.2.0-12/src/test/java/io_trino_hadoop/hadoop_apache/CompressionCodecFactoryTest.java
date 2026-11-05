/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class CompressionCodecFactoryTest {
    @ParameterizedTest
    @ValueSource(strings = {".gz", ".deflate"})
    void discoversCodecsAndRoundTripsData(String extension) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setStrings("io.compression.codecs", GzipCodec.class.getName(), DefaultCodec.class.getName());
        CompressionCodecFactory factory = new CompressionCodecFactory(configuration);
        CompressionCodec codec = factory.getCodec(new Path("records" + extension));
        assertNotNull(codec);
        assertSame(codec, factory.getCodecByClassName(codec.getClass().getName()));
        assertSame(codec, factory.getCodecByName(extension.equals(".gz") ? "GZIP" : "default"));
        assertNull(factory.getCodec(new Path("records.txt")));

        byte[] payload = "row-one,row-two,row-three\n".repeat(30).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (CompressionOutputStream output = codec.createOutputStream(compressed)) {
            output.write(payload);
        }
        try (CompressionInputStream input = codec.createInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            assertArrayEquals(payload, input.readAllBytes());
        }
    }
}
