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
import com.rabbitmq.qpid.protonj2.codec.EncoderState;
import com.rabbitmq.qpid.protonj2.codec.StreamDecoderState;
import com.rabbitmq.qpid.protonj2.codec.decoders.ProtonStreamDecoder;
import com.rabbitmq.qpid.protonj2.codec.encoders.ProtonEncoder;
import com.rabbitmq.qpid.protonj2.types.Symbol;
import java.io.ByteArrayInputStream;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class ProtonStreamDecoderTest {

    @Test
    void readsSingleScalarValueAsMultipleValues() {
        ProtonStreamDecoder decoder = new ProtonStreamDecoder();
        StreamDecoderState decoderState = decoder.newDecoderState();
        ByteArrayInputStream stream = encodeToStream((buffer) -> {
            ProtonEncoder encoder = new ProtonEncoder();
            encoder.writeString(buffer, encoder.newEncoderState(), "rabbitmq");
        });

        String[] values = decoder.readMultiple(stream, decoderState, String.class);

        assertThat(values).containsExactly("rabbitmq");
    }

    @Test
    void reportsUnexpectedTypeForArrayWithDifferentComponentType() {
        ProtonStreamDecoder decoder = new ProtonStreamDecoder();
        StreamDecoderState decoderState = decoder.newDecoderState();
        Symbol[] symbols = new Symbol[] {Symbol.valueOf("orders"), Symbol.valueOf("created")};
        ByteArrayInputStream stream = encodeToStream((buffer) -> {
            ProtonEncoder encoder = new ProtonEncoder();
            EncoderState encoderState = encoder.newEncoderState();
            encoder.writeArray(buffer, encoderState, symbols);
        });

        assertThatThrownBy(() -> decoder.readMultiple(stream, decoderState, String.class))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void reportsUnexpectedTypeForScalarWithDifferentType() {
        ProtonStreamDecoder decoder = new ProtonStreamDecoder();
        StreamDecoderState decoderState = decoder.newDecoderState();
        ByteArrayInputStream stream = encodeToStream((buffer) -> {
            ProtonEncoder encoder = new ProtonEncoder();
            encoder.writeInteger(buffer, encoder.newEncoderState(), 42);
        });

        assertThatThrownBy(() -> decoder.readMultiple(stream, decoderState, String.class))
                .isInstanceOf(ClassCastException.class);
    }

    private static ByteArrayInputStream encodeToStream(Consumer<ProtonBuffer> encoderAction) {
        try (ProtonBuffer buffer = ProtonBufferAllocator.defaultAllocator().allocate(256)) {
            encoderAction.accept(buffer);
            byte[] bytes = new byte[buffer.getReadableBytes()];
            buffer.readBytes(bytes, 0, bytes.length);
            return new ByteArrayInputStream(bytes);
        }
    }
}
