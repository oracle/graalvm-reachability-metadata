/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.eventbus.impl.codecs.SerializableCodec;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SerializableCodecInnerCheckedClassNameObjectInputStreamTest {

    @Test
    void decodeFromWireResolvesAllowedSerializableClass() {
        CodecManager codecManager = new CodecManager();
        codecManager.serializableCheck(className -> className.equals(SerializablePayload.class.getName())
                || className.equals(String.class.getName()));
        SerializableCodec codec = new SerializableCodec(codecManager);
        SerializablePayload original = new SerializablePayload("event", 42);
        Buffer wire = Buffer.buffer();

        codec.encodeToWire(wire, original);
        Object decoded = codec.decodeFromWire(0, wire);

        SerializablePayload decodedPayload = assertInstanceOf(SerializablePayload.class, decoded);
        assertEquals(original, decodedPayload);
    }

    private static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int sequence;

        private SerializablePayload(String name, int sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SerializablePayload that)) {
                return false;
            }
            return sequence == that.sequence && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, sequence);
        }
    }
}
