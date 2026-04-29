/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.Base64;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Test {
    @Test
    void serializesAndDeserializesObjectsThroughBase64() throws Exception {
        SerializablePayload payload = new SerializablePayload("alpha", 42);

        String encoded = Base64.encodeObject(payload);
        Object decoded = Base64.decodeToObject(encoded);

        assertThat(decoded).isEqualTo(payload);
    }

    @Test
    void serializesAndDeserializesGzippedObjectsThroughBase64() throws Exception {
        SerializablePayload payload = new SerializablePayload("bravo", 7);

        String encoded = Base64.encodeObject(payload, Base64.GZIP | Base64.DO_BREAK_LINES);
        Object decoded = Base64.decodeToObject(encoded, Base64.GZIP, Base64Test.class.getClassLoader());

        assertThat(decoded).isEqualTo(payload);
    }

    public static class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int value;

        public SerializablePayload(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if(this == other) {
                return true;
            }
            if(!(other instanceof SerializablePayload)) {
                return false;
            }
            SerializablePayload that = (SerializablePayload)other;
            return value == that.value && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }
}
