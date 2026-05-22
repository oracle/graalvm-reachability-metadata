/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.Serializable;
import java.util.Objects;

import org.jgroups.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Anonymous1Test {
    @Test
    void decodesSerializableObjectWithProvidedClassLoader() throws Exception {
        Payload expected = new Payload("custom-loader", 52);
        ClassLoader loader = Base64Anonymous1Test.class.getClassLoader();

        String encoded = Base64.encodeObject(expected);
        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, loader);

        assertThat(decoded).isEqualTo(expected);
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int value;

        public Payload(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Payload payload)) {
                return false;
            }
            return value == payload.value && Objects.equals(name, payload.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }
}
