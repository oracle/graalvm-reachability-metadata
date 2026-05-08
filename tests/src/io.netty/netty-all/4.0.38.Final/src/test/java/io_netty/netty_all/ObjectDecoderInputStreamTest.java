/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDecoderInputStreamTest {
    @Test
    void readsObjectWrittenByObjectEncoderOutputStream() throws Exception {
        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream out = new ObjectEncoderOutputStream(encodedBytes)) {
            out.writeObject("netty-object-decoder-input-stream");
        }

        try (ObjectDecoderInputStream in = new ObjectDecoderInputStream(
                new ByteArrayInputStream(encodedBytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo("netty-object-decoder-input-stream");
        }
    }
}
