/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CompactObjectInputStreamTest {
    @Test
    void fallsBackToObjectInputStreamResolutionWhenResolverCannotLoadArrayClass() throws Exception {
        int[] expected = new int[] { 3, 1, 4, 1, 5 };
        ByteBuf encoded = Unpooled.wrappedBuffer(serialize(expected));
        EmbeddedChannel decoder = new EmbeddedChannel(new ObjectDecoder(new ArrayFallbackClassResolver()));
        try {
            Assertions.assertTrue(decoder.writeInbound(encoded));

            Object decoded = decoder.readInbound();

            Assertions.assertTrue(decoded instanceof int[]);
            Assertions.assertArrayEquals(expected, (int[]) decoded);
        } finally {
            decoder.finish();
        }
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ObjectEncoderOutputStream objectOutput = new ObjectEncoderOutputStream(output);
        objectOutput.writeObject(value);
        objectOutput.close();
        return output.toByteArray();
    }

    private static final class ArrayFallbackClassResolver implements ClassResolver {
        private final ClassResolver delegate = ClassResolvers.cacheDisabled(CompactObjectInputStreamTest.class.getClassLoader());

        @Override
        public Class<?> resolve(String className) throws ClassNotFoundException {
            if (int[].class.getName().equals(className)) {
                throw new ClassNotFoundException(className);
            }
            return delegate.resolve(className);
        }
    }
}
