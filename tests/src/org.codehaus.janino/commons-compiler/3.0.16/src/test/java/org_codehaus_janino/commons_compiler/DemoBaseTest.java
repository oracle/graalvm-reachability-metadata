/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.samples.DemoBase;
import org.junit.jupiter.api.Test;

public class DemoBaseTest {

    @Test
    void createsObjectsThroughEmptyAndStringConstructors() throws Exception {
        Object defaultInstance = DemoBase.createObject(ConstructibleTarget.class, "");
        Object populatedInstance = DemoBase.createObject(ConstructibleTarget.class, "janino");

        assertThat(defaultInstance).isEqualTo(new ConstructibleTarget("default"));
        assertThat(populatedInstance).isEqualTo(new ConstructibleTarget("janino"));
    }

    @Test
    void resolvesNamedReferenceTypes() {
        assertThat(DemoBase.stringToType("java.lang.String")).isEqualTo(String.class);
    }

    public static final class ConstructibleTarget {
        private final String value;

        public ConstructibleTarget() {
            this("default");
        }

        public ConstructibleTarget(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ConstructibleTarget constructibleTarget)) {
                return false;
            }
            return this.value.equals(constructibleTarget.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
