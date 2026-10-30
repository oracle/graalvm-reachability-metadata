/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.compression.JdkZlibDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.compression.ZlibWrapper;

public class ByteBufChecksumTest {
    @Test
    void validatesAGzipChecksum() throws IOException {
        verifyGzip(false, "checksummed heap payload");
    }

    static void verifyGzip(boolean direct, String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(UTF_8));
        }
        EmbeddedChannel channel = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP));
        ByteBuf input = direct ? Unpooled.directBuffer() : Unpooled.buffer();
        input.writeBytes(bytes.toByteArray());
        try {
            assertTrue(channel.writeInbound(input));
            ByteBuf decoded = channel.readInbound();
            try {
                assertEquals(text, decoded.toString(UTF_8));
            } finally {
                decoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
