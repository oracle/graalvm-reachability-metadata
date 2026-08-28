/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

/** Verifies serialization-constructor creation through Objenesis' reflection factory helper. */
public class SunReflectionFactoryHelperTest {
    @Test
    void createsInstanceWithoutCallingConstructor() {
        SunReflectionFactoryInstantiator<Fixture> instantiator =
                new SunReflectionFactoryInstantiator<>(Fixture.class);

        Fixture fixture = instantiator.newInstance();

        assertThat(fixture.value).isNull();
    }

    public static final class Fixture {
        private final String value;

        public Fixture() {
            this.value = "constructed";
        }
    }
}
