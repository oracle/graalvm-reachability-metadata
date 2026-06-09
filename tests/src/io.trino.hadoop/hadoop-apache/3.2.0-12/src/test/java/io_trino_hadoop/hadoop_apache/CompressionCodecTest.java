/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.junit.jupiter.api.Test;

public class CompressionCodecTest {
    @Test
    void defaultCodecRoundTripsBytes() throws Exception {
        DefaultCodec codec = new DefaultCodec();
        codec.setConf(new Configuration(false));

        assertThat(roundTrip(codec, "deflate-compressed payload"))
                .isEqualTo("deflate-compressed payload");
        assertThat(codec.getDefaultExtension()).isEqualTo(".deflate");
    }

    @Test
    void bzip2CodecRoundTripsBytes() throws Exception {
        BZip2Codec codec = new BZip2Codec();
        codec.setConf(new Configuration(false));

        assertThat(roundTrip(codec, "bzip2-compressed payload"))
                .isEqualTo("bzip2-compressed payload");
        assertThat(codec.getDefaultExtension()).isEqualTo(".bz2");
    }

    private static String roundTrip(CompressionCodec codec, String value) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (CompressionOutputStream outputStream = codec.createOutputStream(compressed)) {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            outputStream.finish();
        }

        try (CompressionInputStream inputStream =
                codec.createInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
