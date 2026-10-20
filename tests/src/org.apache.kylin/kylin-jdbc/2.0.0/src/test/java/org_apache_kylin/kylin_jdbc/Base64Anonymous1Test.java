/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.io.Serializable;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Anonymous1Test {
    @Test
    void decodesAnObjectWithTheProvidedApplicationClassLoader() throws Exception {
        String encoded = Base64.encodeObject(new Payload("kylin"));

        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, getClass().getClassLoader());

        assertThat(decoded).isEqualTo(new Payload("kylin"));
    }

    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public Payload(String value) {
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
