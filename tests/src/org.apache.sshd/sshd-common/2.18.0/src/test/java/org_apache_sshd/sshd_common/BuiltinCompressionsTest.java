/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.compression.CompressionFactory;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.junit.jupiter.api.Test;

public class BuiltinCompressionsTest {
    @Test
    void compressesAndRestoresBufferPayload() throws IOException {
        byte[] payload = ("SSH compression payload repeated to exercise the streaming "
                + "buffer path. ").repeat(32).getBytes(StandardCharsets.UTF_8);
        CompressionFactory factory = BuiltinCompressions.zlib;

        assertThat(factory.isSupported()).isTrue();
        assertThat(factory.isDelayed()).isFalse();
        assertThat(factory.isCompressionExecuted()).isTrue();

        Compression compressor = factory.create();
        compressor.init(Compression.Type.Deflater, 6);
        ByteArrayBuffer compressedBuffer = new ByteArrayBuffer(payload.length);
        compressedBuffer.putRawBytes(payload);
        compressor.compress(compressedBuffer);
        byte[] compressed = Arrays.copyOfRange(
                compressedBuffer.array(), compressedBuffer.rpos(), compressedBuffer.wpos());

        Compression decompressor = factory.create();
        decompressor.init(Compression.Type.Inflater, 6);
        ByteArrayBuffer encoded = new ByteArrayBuffer(compressed);
        ByteArrayBuffer restored = new ByteArrayBuffer(payload.length);
        decompressor.uncompress(encoded, restored);

        assertThat(compressed).isNotEmpty().hasSizeLessThan(payload.length);
        assertThat(Arrays.copyOfRange(restored.array(), restored.rpos(), restored.wpos()))
                .isEqualTo(payload);
    }
}
