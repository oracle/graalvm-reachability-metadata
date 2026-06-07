/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import static org.assertj.core.api.Assertions.assertThat;

import com.atomikos.util.SerializableObjectFactory;
import java.io.Serializable;
import javax.naming.Reference;
import org.junit.jupiter.api.Test;

public class SerializableObjectFactoryTest {
    @Test
    void createsReferenceAndRestoresSerializableObject() throws Exception {
        SerializablePayload payload = new SerializablePayload("transaction-service", 42);

        Reference reference = SerializableObjectFactory.createReference(payload);
        Object restored = new SerializableObjectFactory().getObjectInstance(reference, null, null, null);

        assertThat(reference.getClassName()).isEqualTo(SerializablePayload.class.getName());
        assertThat(restored).isEqualTo(payload);
    }

    public static final class SerializablePayload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int count;

        SerializablePayload(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SerializablePayload)) {
                return false;
            }
            SerializablePayload that = (SerializablePayload) other;
            return count == that.count && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + count;
        }
    }
}
