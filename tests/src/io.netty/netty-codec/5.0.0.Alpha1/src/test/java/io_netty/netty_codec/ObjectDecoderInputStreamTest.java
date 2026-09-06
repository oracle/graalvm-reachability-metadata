/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectDecoderInputStreamTest {
    @Test
    void readsObjectWrittenByObjectEncoderOutputStream() throws Exception {
        String expectedMessage = "netty object decoder input stream";
        byte[] encodedMessage = encodeObject(expectedMessage);

        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(
                new ByteArrayInputStream(encodedMessage), encodedMessage.length)) {
            Object actualMessage = input.readObject();

            assertEquals(expectedMessage, actualMessage);
            assertEquals(0, input.available());
        }
    }

    private static byte[] encodeObject(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream encoder = new ObjectEncoderOutputStream(output)) {
            encoder.writeObject(value);
        }
        return output.toByteArray();
    }
}
