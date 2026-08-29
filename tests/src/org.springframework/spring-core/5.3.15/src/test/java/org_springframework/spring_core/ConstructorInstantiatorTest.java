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

/** Verifies constructor-backed Objenesis instantiation. */
public class ConstructorInstantiatorTest {
    @Test
    void constructsInstanceWithNoArgConstructor() {
        ConstructorInstantiator<Fixture> instantiator = new ConstructorInstantiator<>(Fixture.class);

        Fixture fixture = instantiator.newInstance();

        assertThat(fixture.value).isEqualTo("constructed");
    }

    public static final class Fixture {
        private final String value;

        public Fixture() {
            this.value = "constructed";
        }
    }
}
