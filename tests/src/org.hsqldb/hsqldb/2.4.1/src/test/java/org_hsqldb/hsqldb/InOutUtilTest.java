/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.hsqldb.lib.InOutUtil;
import org.junit.jupiter.api.Test;

public class InOutUtilTest {
    @Test
    void serializeAndDeserializeRoundTripsSerializableValue() throws Exception {
        SerializableValue original = new SerializableValue(241, 1972L, true);

        byte[] serialized = InOutUtil.serialize(original);
        Serializable deserialized = InOutUtil.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isEqualTo(original);
    }

    private static final class SerializableValue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int number;
        private final long sequence;
        private final boolean enabled;

        private SerializableValue(int number, long sequence, boolean enabled) {
            this.number = number;
            this.sequence = sequence;
            this.enabled = enabled;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof SerializableValue)) {
                return false;
            }

            SerializableValue value = (SerializableValue) object;

            return number == value.number && sequence == value.sequence && enabled == value.enabled;
        }

        @Override
        public int hashCode() {
            int result = number;

            result = 31 * result + Long.hashCode(sequence);
            result = 31 * result + Boolean.hashCode(enabled);

            return result;
        }
    }
}
