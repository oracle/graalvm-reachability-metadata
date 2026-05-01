/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompactObjectInputStreamTest {
    @Test
    void resolvesFatArrayDescriptorThroughObjectInputStreamFallback() throws Exception {
        String[] expected = {"netty", "codec", "serialization"};
        byte[] encoded = encodeObject(expected);
        EmbeddedChannel channel = new EmbeddedChannel(new ObjectDecoder(new FailingClassResolver()));

        try {
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(encoded)));

            Object decoded = channel.readInbound();
            String[] actual = assertInstanceOf(String[].class, decoded);

            assertArrayEquals(expected, actual);
            assertNull(channel.readInbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static byte[] encodeObject(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream encoder = new ObjectEncoderOutputStream(output)) {
            encoder.writeObject(value);
        }
        return output.toByteArray();
    }

    private static final class FailingClassResolver implements ClassResolver {
        @Override
        public Class<?> resolve(String className) throws ClassNotFoundException {
            throw new ClassNotFoundException(className);
        }
    }
}
