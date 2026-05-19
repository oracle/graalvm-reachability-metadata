/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.rabbitmq.qpid.protonj2.buffer.ProtonBuffer;
import com.rabbitmq.qpid.protonj2.buffer.ProtonBufferAllocator;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonDecoder;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonStreamDecoder;
import com.rabbitmq.qpid.protonj2.codec.decoders.primitives.Integer32TypeDecoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPrimitiveTypeDecoderTest {
    @Test
    void readArrayElementsFromBufferCreatesTypedObjectArray() {
        Integer32TypeDecoder elementDecoder = new Integer32TypeDecoder();
        ProtonDecoder protonDecoder = new ProtonDecoder();

        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(8)) {
            buffer.writeInt(41);
            buffer.writeInt(42);

            Integer[] values = elementDecoder.readArrayElements(
                    buffer,
                    protonDecoder.getCachedDecoderState(),
                    2);

            assertThat(values).containsExactly(41, 42);
        }
    }

    @Test
    void readArrayElementsFromStreamCreatesTypedObjectArray() {
        Integer32TypeDecoder elementDecoder = new Integer32TypeDecoder();
        ProtonStreamDecoder protonDecoder = new ProtonStreamDecoder();
        InputStream stream = integerStream(43, 44);

        Integer[] values = elementDecoder.readArrayElements(
                stream,
                protonDecoder.getCachedDecoderState(),
                2);

        assertThat(values).containsExactly(43, 44);
    }

    private static InputStream integerStream(int first, int second) {
        byte[] bytes = ByteBuffer.allocate(8).putInt(first).putInt(second).array();
        return new ByteArrayInputStream(bytes);
    }
}
