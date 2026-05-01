/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ByteBufChecksumInnerReflectiveByteBufChecksumTest {
    @Test
    void gzipDecoderUpdatesChecksumFromDirectInputBuffer() throws IOException {
        byte[] expected = "netty checksum data from a direct buffer".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = gzip(expected);
        ByteBuf input = Unpooled.directBuffer(compressed.length);
        EmbeddedChannel channel = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP));

        try {
            input.writeBytes(compressed);

            assertTrue(channel.writeInbound(input));
            input = null;

            ByteBuf decoded = channel.readInbound();
            try {
                byte[] actual = new byte[decoded.readableBytes()];
                decoded.readBytes(actual);

                assertArrayEquals(expected, actual);
            } finally {
                decoded.release();
            }
            assertNull(channel.readInbound());
        } finally {
            if (input != null && input.refCnt() > 0) {
                input.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] gzip(byte[] value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutput = new GZIPOutputStream(output)) {
            gzipOutput.write(value);
        }
        return output.toByteArray();
    }
}
