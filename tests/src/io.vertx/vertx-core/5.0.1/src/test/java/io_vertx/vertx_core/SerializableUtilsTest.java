/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.impl.SerializableUtils;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SerializableUtilsTest {

    @Test
    void roundTripSerializableObjectThroughBytes() {
        SerializablePayload original = new SerializablePayload("payload", 7, List.of("alpha", "beta"));

        byte[] bytes = SerializableUtils.toBytes(original);
        Object restored = SerializableUtils.fromBytes(bytes, ObjectInputStream::new);

        SerializablePayload restoredPayload = assertInstanceOf(SerializablePayload.class, restored);
        assertNotSame(original, restoredPayload);
        assertEquals(original, restoredPayload);
    }

    private static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int priority;
        private final List<String> tags;

        private SerializablePayload(String name, int priority, List<String> tags) {
            this.name = name;
            this.priority = priority;
            this.tags = tags;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SerializablePayload that)) {
                return false;
            }
            return priority == that.priority && Objects.equals(name, that.name) && Objects.equals(tags, that.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, priority, tags);
        }
    }
}
