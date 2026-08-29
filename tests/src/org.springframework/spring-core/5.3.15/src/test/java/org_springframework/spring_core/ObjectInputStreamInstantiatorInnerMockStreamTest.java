/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.basic.ObjectInputStreamInstantiator;

/** Verifies serialization descriptor creation for Objenesis mock streams. */
public class ObjectInputStreamInstantiatorInnerMockStreamTest {
    @Test
    void createsRepeatedInstancesFromMockStream() {
        ObjectInputStreamInstantiator<Fixture> instantiator =
                new ObjectInputStreamInstantiator<>(Fixture.class);

        Fixture first = instantiator.newInstance();
        Fixture second = instantiator.newInstance();

        assertThat(first).isNotSameAs(second);
        assertThat(first.initialized).isFalse();
        assertThat(second.initialized).isFalse();
    }

    public static final class Fixture implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final boolean initialized;

        public Fixture() {
            initialized = true;
        }
    }
}
