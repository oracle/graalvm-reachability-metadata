/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

/** Verifies constructor-bypassing instantiation through Unsafe. */
public class UnsafeFactoryInstantiatorTest {
    @Test
    void allocatesObjectWithoutInvokingConstructor() {
        UnsafeFactoryInstantiator<Fixture> instantiator =
                new UnsafeFactoryInstantiator<>(Fixture.class);

        Fixture fixture = instantiator.newInstance();

        assertThat(fixture.initialized).isFalse();
    }

    public static final class Fixture {
        private final boolean initialized;

        public Fixture() {
            initialized = true;
        }
    }
}
