/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.basic.ConstructorInstantiator;

/** Verifies Objenesis instantiation through a default constructor. */
public class ConstructorInstantiatorTest {
    @Test
    void invokesDefaultConstructor() {
        ConstructorInstantiator<Constructed> instantiator =
                new ConstructorInstantiator<>(Constructed.class);

        Constructed instance = instantiator.newInstance();

        assertThat(instance.value).isEqualTo("constructed");
    }

    public static final class Constructed {
        private final String value;

        public Constructed() {
            value = "constructed";
        }
    }
}
