/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.functors.PrototypeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrototypeSerializationFactoryTest {

    @Test
    void createsCopiesThroughSerializationWhenCloneAndCopyConstructorAreUnavailable() {
        SerializableOnlyValue prototype = new SerializableOnlyValue("metadata", List.of("alpha", "beta"));

        Factory<SerializableOnlyValue> factory = PrototypeFactory.prototypeFactory(prototype);
        SerializableOnlyValue created = factory.create();
        created.tags().add("gamma");

        assertThat(created).isNotSameAs(prototype);
        assertThat(created.description()).isEqualTo("metadata:[alpha, beta, gamma]");
        assertThat(prototype.description()).isEqualTo("metadata:[alpha, beta]");
        assertThat(created.tags()).isNotSameAs(prototype.tags());
    }

    public static final class SerializableOnlyValue implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String name;
        private final ArrayList<String> tags;

        public SerializableOnlyValue(String name, List<String> tags) {
            this.name = name;
            this.tags = new ArrayList<>(tags);
        }

        public String description() {
            return name + ":" + tags;
        }

        public ArrayList<String> tags() {
            return tags;
        }
    }
}
