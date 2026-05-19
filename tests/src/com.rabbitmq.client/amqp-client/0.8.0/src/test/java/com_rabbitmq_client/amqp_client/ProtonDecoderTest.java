/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import com.rabbitmq.qpid.protonj2.buffer.ProtonBuffer;
import com.rabbitmq.qpid.protonj2.buffer.ProtonBufferAllocator;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonDecoder;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProtonDecoderTest {
    @Test
    void readMultipleWrapsAssignableScalarInTypedArray() {
        ProtonDecoder decoder = new ProtonDecoder();
        ProtonEncoder encoder = new ProtonEncoder();

        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64)) {
            encoder.writeString(buffer, encoder.getCachedEncoderState(), "value");

            String[] values = decoder.readMultiple(buffer, decoder.getCachedDecoderState(), String.class);

            assertThat(values).containsExactly("value");
        }
    }

    @Test
    void readMultipleRejectsArrayWithUnexpectedComponentType() {
        ProtonDecoder decoder = new ProtonDecoder();
        ProtonEncoder encoder = new ProtonEncoder();

        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64)) {
            encoder.writeArray(buffer, encoder.getCachedEncoderState(), new int[] {1, 2});

            assertThatThrownBy(() -> decoder.readMultiple(buffer, decoder.getCachedDecoderState(), String.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Test
    void readMultipleRejectsScalarWithUnexpectedType() {
        ProtonDecoder decoder = new ProtonDecoder();
        ProtonEncoder encoder = new ProtonEncoder();

        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(64)) {
            encoder.writeString(buffer, encoder.getCachedEncoderState(), "not an integer");

            assertThatThrownBy(() -> decoder.readMultiple(buffer, decoder.getCachedDecoderState(), Integer.class))
                    .isInstanceOf(ClassCastException.class);
        }
    }
}
