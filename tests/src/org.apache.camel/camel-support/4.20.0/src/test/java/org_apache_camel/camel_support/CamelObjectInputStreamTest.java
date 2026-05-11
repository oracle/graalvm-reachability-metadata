/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.CamelObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CamelObjectInputStreamTest {
    @Test
    void readObjectFallsBackToObjectInputStreamClassResolutionWhenContextIsNull() throws Exception {
        byte[] serializedPayload = serialize(new CamelObjectInputStreamPayload("fallback"));

        try (ObjectInputStream inputStream = new CamelObjectInputStream(new ByteArrayInputStream(serializedPayload), null)) {
            Object deserialized = inputStream.readObject();

            assertThat(deserialized).isInstanceOf(CamelObjectInputStreamPayload.class);
            assertThat(((CamelObjectInputStreamPayload) deserialized).value()).isEqualTo("fallback");
        }
    }

    @Test
    void readObjectUsesApplicationContextClassLoaderWhenContextIsAvailable() throws Exception {
        byte[] serializedPayload = serialize(new CamelObjectInputStreamPayload("application-loader"));

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.setApplicationContextClassLoader(CamelObjectInputStreamTest.class.getClassLoader());

            try (ObjectInputStream inputStream = new CamelObjectInputStream(new ByteArrayInputStream(serializedPayload), context)) {
                Object deserialized = inputStream.readObject();

                assertThat(deserialized).isInstanceOf(CamelObjectInputStreamPayload.class);
                assertThat(((CamelObjectInputStreamPayload) deserialized).value()).isEqualTo("application-loader");
            }
        }
    }

    private static byte[] serialize(CamelObjectInputStreamPayload payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(payload);
        }
        return bytes.toByteArray();
    }

    private static final class CamelObjectInputStreamPayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        private CamelObjectInputStreamPayload(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
