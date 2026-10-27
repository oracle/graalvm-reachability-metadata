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

/** Verifies initialization and use of Objenesis' shared Unsafe access. */
public class UnsafeUtilsTest {
    @Test
    void suppliesUnsafeForConstructorFreeAllocation() {
        Fixture fixture = new UnsafeFactoryInstantiator<>(Fixture.class).newInstance();

        assertThat(fixture.initialized).isFalse();
    }

    public static final class Fixture {
        private final boolean initialized;

        public Fixture() {
            this.initialized = true;
        }
    }
}
