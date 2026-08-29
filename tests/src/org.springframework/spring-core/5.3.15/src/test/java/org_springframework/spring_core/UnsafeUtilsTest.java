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

/** Verifies initialization of the Unsafe support used by Objenesis. */
public class UnsafeUtilsTest {
    @Test
    void providesUnsafeForObjectAllocation() {
        UnsafeFactoryInstantiator<Fixture> instantiator =
                new UnsafeFactoryInstantiator<>(Fixture.class);

        assertThat(instantiator.newInstance()).isNotNull();
    }

    public static final class Fixture {
        private Fixture(String ignored) {}
    }
}
