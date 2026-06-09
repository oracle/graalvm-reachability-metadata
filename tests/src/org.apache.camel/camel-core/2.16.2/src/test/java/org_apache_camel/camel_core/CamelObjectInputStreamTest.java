/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CamelObjectInputStream;
import org.junit.jupiter.api.Test;

public class CamelObjectInputStreamTest {
    @Test
    void deserializesUsingCamelApplicationContextClassLoader() throws Exception {
        byte[] serialized = serialize(new SerializablePayload("application-loader"));
        DefaultCamelContext context = new DefaultCamelContext();
        context.setApplicationContextClassLoader(CamelObjectInputStreamTest.class.getClassLoader());

        Object deserialized;
        try (CamelObjectInputStream input = new CamelObjectInputStream(new ByteArrayInputStream(serialized), context)) {
            deserialized = input.readObject();
        } finally {
            context.stop();
        }

        assertThat(deserialized).isEqualTo(new SerializablePayload("application-loader"));
    }

    @Test
    void deserializesUsingDefaultObjectInputStreamResolutionWithoutCamelContext() throws Exception {
        byte[] serialized = serialize(new SerializablePayload("default-loader"));

        Object deserialized;
        try (CamelObjectInputStream input = new CamelObjectInputStream(new ByteArrayInputStream(serialized), null)) {
            deserialized = input.readObject();
        }

        assertThat(deserialized).isEqualTo(new SerializablePayload("default-loader"));
    }

    private static byte[] serialize(SerializablePayload payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(payload);
        }
        return bytes.toByteArray();
    }

    public static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        private SerializablePayload(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializablePayload)) {
                return false;
            }
            SerializablePayload payload = (SerializablePayload) other;
            return value.equals(payload.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
