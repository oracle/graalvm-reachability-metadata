/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class SerializationUtilsTest {

    @Test
    public void cloneCreatesIndependentCopyOfSerializableObjectGraph() {
        SerializableContainer original = new SerializableContainer("alpha", new ArrayList<>(List.of("one", "two")));

        SerializableContainer clone = SerializationUtils.clone(original);
        clone.getValues().add("three");

        assertThat(clone).isNotSameAs(original);
        assertThat(clone.getName()).isEqualTo("alpha");
        assertThat(clone.getValues()).containsExactly("one", "two", "three");
        assertThat(original.getValues()).containsExactly("one", "two");
    }

    @Test
    public void serializeAndDeserializeRoundTripSerializableObject() {
        SerializableContainer original = new SerializableContainer("beta", new ArrayList<>(List.of("x", "y")));

        byte[] serialized = SerializationUtils.serialize(original);
        SerializableContainer deserialized = SerializationUtils.deserialize(serialized);

        assertThat(serialized).isNotEmpty();
        assertThat(deserialized).isNotSameAs(original);
        assertThat(deserialized.getName()).isEqualTo("beta");
        assertThat(deserialized.getValues()).containsExactly("x", "y");
    }

    public static final class SerializableContainer implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final List<String> values;

        public SerializableContainer(String name, List<String> values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public List<String> getValues() {
            return values;
        }
    }
}
