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

/** Verifies object instantiation through a synthetic serialization stream. */
public class ObjectInputStreamInstantiatorTest {
    @Test
    void readsObjectWithoutInvokingSerializableConstructor() {
        ObjectInputStreamInstantiator<Fixture> instantiator =
                new ObjectInputStreamInstantiator<>(Fixture.class);

        Fixture fixture = instantiator.newInstance();

        assertThat(fixture.value).isNull();
    }

    public static final class Fixture implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private final String value;

        public Fixture() {
            value = "initialized";
        }
    }
}
