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

/** Verifies reflective construction of the configured object instantiator. */
public class SingleInstantiatorStrategyTest {
    @Test
    void createsConfiguredInstantiatorForRequestedType() {
        SingleInstantiatorStrategy strategy =
                new SingleInstantiatorStrategy(ConstructorInstantiator.class);

        ObjectInstantiator<Fixture> instantiator = strategy.newInstantiatorOf(Fixture.class);
        Fixture fixture = instantiator.newInstance();

        assertThat(instantiator).isInstanceOf(ConstructorInstantiator.class);
        assertThat(fixture.value).isEqualTo("constructed");
    }

    public static final class Fixture {
        private final String value;

        public Fixture() {
            this.value = "constructed";
        }
    }
}
