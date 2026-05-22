/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.qpid.protonj2.buffer.ProtonBuffer;
import com.rabbitmq.qpid.protonj2.buffer.ProtonBufferAllocator;
import com.rabbitmq.qpid.protonj2.buffer.ProtonBufferInputStream;
import com.rabbitmq.qpid.protonj2.codec.StreamDecoderState;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonStreamDecoder;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonStreamDecoderFactory;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoder;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoderFactory;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoderState;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ProtonStreamDecoderTest {

    @Test
    void readMultipleWrapsSingleStreamValueInTypedArray() throws IOException {
        ProtonStreamDecoder decoder = ProtonStreamDecoderFactory.create();
        StreamDecoderState decoderState = decoder.newDecoderState();

        try (ProtonBufferInputStream stream = new ProtonBufferInputStream(encodeString("value"))) {
            String[] values = decoder.readMultiple(stream, decoderState, String.class);

            assertThat(values).containsExactly("value");
        }
    }

    @Test
    void readMultipleRejectsSingleStreamValueOfUnexpectedType() throws IOException {
        ProtonStreamDecoder decoder = ProtonStreamDecoderFactory.create();
        StreamDecoderState decoderState = decoder.newDecoderState();

        try (ProtonBufferInputStream stream = new ProtonBufferInputStream(encodeString("value"))) {
            assertThatThrownBy(() -> decoder.readMultiple(stream, decoderState, Integer.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Test
    void readMultipleRejectsStreamArrayValueWithUnexpectedComponentType() throws IOException {
        ProtonStreamDecoder decoder = ProtonStreamDecoderFactory.create();
        StreamDecoderState decoderState = decoder.newDecoderState();

        try (ProtonBufferInputStream stream = new ProtonBufferInputStream(encodeStringArray("one", "two"))) {
            assertThatThrownBy(() -> decoder.readMultiple(stream, decoderState, Integer.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    private static ProtonBuffer encodeString(String value) {
        ProtonEncoder encoder = ProtonEncoderFactory.create();
        ProtonEncoderState encoderState = encoder.newEncoderState();
        ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64);
        encoder.writeString(buffer, encoderState, value);
        return buffer;
    }

    private static ProtonBuffer encodeStringArray(String... values) {
        ProtonEncoder encoder = ProtonEncoderFactory.create();
        ProtonEncoderState encoderState = encoder.newEncoderState();
        ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(128);
        encoder.writeArray(buffer, encoderState, values);
        return buffer;
    }
}
