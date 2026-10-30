/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;

public class ObjectEncoderOutputStreamTest {
    @Test
    void writesIndependentObjectsAndPrimitiveData() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes, 8)) {
            output.writeInt(17);
            output.writeObject("first object");
            output.writeObject("second object");
        }
        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(17, input.readInt());
            assertEquals("first object", input.readObject());
            assertEquals("second object", input.readObject());
            assertEquals(0, input.available());
        }
    }
}
