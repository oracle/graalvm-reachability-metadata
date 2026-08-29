/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.util.SerializationUtils;

/** Verifies deserialization with an explicitly configured class loader. */
public class ConfigurableObjectInputStreamTest {
    @Test
    void resolvesSerializedClassWithConfiguredLoader() throws Exception {
        Payload payload = new Payload("spring");
        byte[] bytes = SerializationUtils.serialize(payload);

        try (ConfigurableObjectInputStream input = new ConfigurableObjectInputStream(
                new ByteArrayInputStream(bytes), getClass().getClassLoader())) {
            assertThat(input.readObject()).isEqualTo(payload);
        }
    }

    private static final class Payload implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final String value;

        private Payload(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Payload && value.equals(((Payload) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
