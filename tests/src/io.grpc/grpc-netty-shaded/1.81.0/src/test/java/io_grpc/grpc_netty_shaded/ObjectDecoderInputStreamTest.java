/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;

public class ObjectDecoderInputStreamTest {
    @Test
    void interleavesPrimitiveValuesAndSerializedObjects() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeInt(73);
            output.writeObject(new long[]{13, 21});
            output.writeUTF("tail");
        }
        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(73, input.readInt());
            assertArrayEquals(new long[]{13, 21}, (long[]) input.readObject());
            assertEquals("tail", input.readUTF());
        }
    }
}
