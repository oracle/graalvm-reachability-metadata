/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.rabbitmq.qpid.protonj2.buffer.ProtonBuffer;
import com.rabbitmq.qpid.protonj2.buffer.ProtonBufferAllocator;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonStreamDecoder;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProtonStreamDecoderTest {
    @Test
    void readMultipleWrapsAssignableScalarInTypedArray() {
        ProtonStreamDecoder decoder = new ProtonStreamDecoder();
        InputStream stream = streamFromString("value");

        String[] values = decoder.readMultiple(stream, decoder.getCachedDecoderState(), String.class);

        assertThat(values).containsExactly("value");
    }

    @Test
    void readMultipleRejectsArrayWithUnexpectedComponentType() {
        ProtonStreamDecoder decoder = new ProtonStreamDecoder();
        InputStream stream = streamFromIntegerArray(new int[] {1, 2});

        assertThatThrownBy(() -> decoder.readMultiple(stream, decoder.getCachedDecoderState(), String.class))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void readMultipleRejectsScalarWithUnexpectedType() {
        ProtonStreamDecoder decoder = new ProtonStreamDecoder();
        InputStream stream = streamFromString("not an integer");

        assertThatThrownBy(() -> decoder.readMultiple(stream, decoder.getCachedDecoderState(), Integer.class))
                .isInstanceOf(ClassCastException.class);
    }

    private static InputStream streamFromString(String value) {
        ProtonEncoder encoder = new ProtonEncoder();

        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64)) {
            encoder.writeString(buffer, encoder.getCachedEncoderState(), value);
            return toInputStream(buffer);
        }
    }

    private static InputStream streamFromIntegerArray(int[] values) {
        ProtonEncoder encoder = new ProtonEncoder();

        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64)) {
            encoder.writeArray(buffer, encoder.getCachedEncoderState(), values);
            return toInputStream(buffer);
        }
    }

    private static InputStream toInputStream(ProtonBuffer buffer) {
        byte[] bytes = new byte[buffer.getReadableBytes()];
        buffer.copyInto(buffer.getReadOffset(), bytes, 0, bytes.length);
        return new ByteArrayInputStream(bytes);
    }
}
