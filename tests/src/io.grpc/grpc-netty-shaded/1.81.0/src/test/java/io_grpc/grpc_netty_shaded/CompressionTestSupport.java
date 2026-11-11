/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;

import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.handler.codec.compression.JdkZlibDecoder;
import io.grpc.netty.shaded.io.netty.handler.codec.compression.JdkZlibEncoder;
import io.grpc.netty.shaded.io.netty.handler.codec.compression.ZlibWrapper;
import io.grpc.netty.shaded.io.netty.util.CharsetUtil;

final class CompressionTestSupport {
    private CompressionTestSupport() {
    }

    static void roundTrip(boolean direct) {
        String text = "gzip payload with checksum validation".repeat(20);
        EmbeddedChannel encoder = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.GZIP));
        EmbeddedChannel decoder = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP));
        ByteBuf compressed = direct ? Unpooled.directBuffer() : Unpooled.buffer();
        ByteBuf decoded = Unpooled.buffer();
        try {
            assertTrue(encoder.writeOutbound(Unpooled.copiedBuffer(text, CharsetUtil.UTF_8)));
            encoder.finish();
            ByteBuf packet;
            while ((packet = encoder.readOutbound()) != null) {
                try {
                    compressed.writeBytes(packet);
                } finally {
                    packet.release();
                }
            }
            assertEquals(direct, compressed.isDirect());
            assertTrue(decoder.writeInbound(compressed.retain()));
            while ((packet = decoder.readInbound()) != null) {
                try {
                    decoded.writeBytes(packet);
                } finally {
                    packet.release();
                }
            }
            assertEquals(text, decoded.toString(CharsetUtil.UTF_8));
            Assertions.assertFalse(decoder.finish());
        } finally {
            compressed.release();
            decoded.release();
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }
}
