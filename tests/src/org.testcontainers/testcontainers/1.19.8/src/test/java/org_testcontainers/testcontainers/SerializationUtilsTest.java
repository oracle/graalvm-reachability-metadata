/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializationUtilsTest {
    @Test
    void serializesDeserializesAndClonesValues() {
        SerializableValue original = new SerializableValue("round trip");
        byte[] encoded = SerializationUtils.serialize(original);

        assertThat(SerializationUtils.<SerializableValue>deserialize(encoded).value).isEqualTo("round trip");
        assertThat(SerializationUtils.clone(original).value).isEqualTo("round trip");
    }

    public static class SerializableValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String value;

        public SerializableValue(String value) {
            this.value = value;
        }
    }
}
