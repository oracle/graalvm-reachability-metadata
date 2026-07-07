/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import org.mongodb.morphia.mapping.Serializer;

import java.io.Serializable;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializerTest {
    @Test
    void roundTripsSerializableObjectThroughByteArray() throws Exception {
        SerializerPayload payload = new SerializerPayload("alpha", 7);

        byte[] serialized = Serializer.serialize(payload, false);
        Object deserialized = Serializer.deserialize(serialized, false);

        assertThat(deserialized).isEqualTo(payload);
    }

    @Test
    void roundTripsCompressedSerializableObjectThroughBinary() throws Exception {
        SerializerPayload payload = new SerializerPayload("bravo", 11);

        byte[] serialized = Serializer.serialize(payload, true);
        Object deserialized = Serializer.deserialize(new Binary(serialized), true);

        assertThat(deserialized).isEqualTo(payload);
    }

    public static class SerializerPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int value;

        public SerializerPayload(final String name, final int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(final Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializerPayload)) {
                return false;
            }
            SerializerPayload that = (SerializerPayload) other;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }
}
