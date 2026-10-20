/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {
    @Test
    void serializesDeserializesAndClonesSerializableValues() {
        SerializableValue input = new SerializableValue("pulsar", 4);

        byte[] bytes = SerializationUtils.serialize(input);
        SerializableValue restored = SerializationUtils.deserialize(bytes);
        SerializableValue cloned = SerializationUtils.clone(input);

        assertThat(restored).usingRecursiveComparison().isEqualTo(input);
        assertThat(cloned).usingRecursiveComparison().isEqualTo(input);
        assertThat(cloned).isNotSameAs(input);
    }

    public static class SerializableValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        public SerializableValue(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
