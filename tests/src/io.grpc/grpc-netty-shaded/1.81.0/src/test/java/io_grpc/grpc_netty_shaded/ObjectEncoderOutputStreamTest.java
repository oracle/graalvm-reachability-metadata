/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.DefaultChannelId;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.grpc.netty.shaded.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;

public class ObjectEncoderOutputStreamTest {
    @Test
    void writesIndependentObjectsToTheSameStream() throws Exception {
        DefaultChannelId id = DefaultChannelId.newInstance();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes, 32)) {
            output.writeObject(id);
            output.writeObject("next message");
            output.flush();
            assertTrue(output.size() > 8);
        }
        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(id, input.readObject());
            assertEquals("next message", input.readObject());
            assertEquals(-1, input.read());
        }
    }
}
