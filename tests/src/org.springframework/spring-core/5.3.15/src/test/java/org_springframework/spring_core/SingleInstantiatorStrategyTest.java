/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.basic.ConstructorInstantiator;
import org.springframework.objenesis.strategy.SingleInstantiatorStrategy;

/** Verifies reflective instantiator construction by a fixed strategy. */
public class SingleInstantiatorStrategyTest {
    @Test
    void createsConfiguredInstantiator() {
        SingleInstantiatorStrategy strategy =
                new SingleInstantiatorStrategy(ConstructorInstantiator.class);

        ObjectInstantiator<Fixture> instantiator = strategy.newInstantiatorOf(Fixture.class);

        assertThat(instantiator.newInstance().initialized).isTrue();
    }

    public static final class Fixture {
        private final boolean initialized;

        public Fixture() {
            initialized = true;
        }
    }
}
