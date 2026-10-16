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
    void serializesDeserializesAndClonesValues() {
        final SerializableValue value = new SerializableValue("persistent://tenant/ns/topic", 4);
        final byte[] bytes = SerializationUtils.serialize(value);

        assertThat(SerializationUtils.<SerializableValue>deserialize(bytes))
                .usingRecursiveComparison()
                .isEqualTo(value);
        assertThat(SerializationUtils.clone(value)).usingRecursiveComparison().isEqualTo(value);
    }

    public static final class SerializableValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String topic;
        private final int partitions;

        public SerializableValue(String topic, int partitions) {
            this.topic = topic;
            this.partitions = partitions;
        }
    }
}
