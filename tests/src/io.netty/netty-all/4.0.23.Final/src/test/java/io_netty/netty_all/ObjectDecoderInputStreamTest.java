/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ObjectDecoderInputStreamTest {
    @Test
    void readsObjectWrittenByObjectEncoderOutputStream() throws Exception {
        DecodedMessage expected = new DecodedMessage("decoded-message", 42);

        Object decoded = decode(encode(expected));

        Assertions.assertTrue(decoded instanceof DecodedMessage);
        DecodedMessage message = (DecodedMessage) decoded;
        Assertions.assertEquals(expected.text, message.text);
        Assertions.assertEquals(expected.sequence, message.sequence);
    }

    private static byte[] encode(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object decode(byte[] bytes) throws IOException, ClassNotFoundException {
        ClassLoader classLoader = ObjectDecoderInputStreamTest.class.getClassLoader();
        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(new ByteArrayInputStream(bytes), classLoader)) {
            return input.readObject();
        }
    }

    public static final class DecodedMessage implements Serializable {
        private static final long serialVersionUID = 1L;

        private String text;
        private int sequence;

        public DecodedMessage(String text, int sequence) {
            this.text = text;
            this.sequence = sequence;
        }
    }
}
