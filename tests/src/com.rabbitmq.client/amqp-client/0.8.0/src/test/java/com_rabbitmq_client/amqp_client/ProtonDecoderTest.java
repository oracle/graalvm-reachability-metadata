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
import com.rabbitmq.qpid.protonj2.codec.DecoderState;
import com.rabbitmq.qpid.protonj2.codec.EncoderState;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonDecoder;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoder;
import com.rabbitmq.qpid.protonj2.types.Symbol;
import org.junit.jupiter.api.Test;

public class ProtonDecoderTest {

    @Test
    void readsSingleScalarValueAsMultipleValues() {
        ProtonDecoder decoder = new ProtonDecoder();
        ProtonBuffer buffer = encodeString("rabbitmq");

        try (buffer) {
            String[] values = decoder.readMultiple(buffer, decoder.newDecoderState(), String.class);

            assertThat(values).containsExactly("rabbitmq");
        }
    }

    @Test
    void reportsUnexpectedTypeForArrayWithDifferentComponentType() {
        ProtonDecoder decoder = new ProtonDecoder();
        ProtonEncoder encoder = new ProtonEncoder();
        ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64);
        Symbol[] symbols = new Symbol[] {Symbol.valueOf("orders"), Symbol.valueOf("created")};
        EncoderState encoderState = encoder.newEncoderState();
        DecoderState decoderState = decoder.newDecoderState();
        encoder.writeArray(buffer, encoderState, symbols);

        try (buffer) {
            assertThatThrownBy(() -> decoder.readMultiple(buffer, decoderState, String.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Test
    void reportsUnexpectedTypeForScalarWithDifferentType() {
        ProtonDecoder decoder = new ProtonDecoder();
        ProtonEncoder encoder = new ProtonEncoder();
        ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64);
        EncoderState encoderState = encoder.newEncoderState();
        DecoderState decoderState = decoder.newDecoderState();
        encoder.writeInteger(buffer, encoderState, 42);

        try (buffer) {
            assertThatThrownBy(() -> decoder.readMultiple(buffer, decoderState, String.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    private static ProtonBuffer encodeString(String value) {
        ProtonEncoder encoder = new ProtonEncoder();
        ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64);
        encoder.writeString(buffer, encoder.newEncoderState(), value);
        return buffer;
    }
}
