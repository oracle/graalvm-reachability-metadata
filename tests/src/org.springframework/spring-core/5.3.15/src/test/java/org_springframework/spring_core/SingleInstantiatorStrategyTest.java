/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.strategy.SingleInstantiatorStrategy;

public class SingleInstantiatorStrategyTest {

    @Test
    void createsObjectInstantiatorsThroughConfiguredConstructor() {
        StrategyInstantiator.constructorCalls.set(0);
        StrategyTarget.constructorCalls.set(0);

        SingleInstantiatorStrategy strategy = new SingleInstantiatorStrategy(StrategyInstantiator.class);
        ObjectInstantiator<StrategyTarget> instantiator = strategy.newInstantiatorOf(StrategyTarget.class);
        StrategyTarget instance = instantiator.newInstance();

        assertThat(instantiator).isInstanceOf(StrategyInstantiator.class);
        assertThat(StrategyInstantiator.constructorCalls).hasValue(1);
        assertThat(instance).isNotNull();
        assertThat(instance.type()).isEqualTo(StrategyTarget.class);
        assertThat(StrategyTarget.constructorCalls).hasValue(1);
    }

    public static final class StrategyInstantiator implements ObjectInstantiator<StrategyTarget> {

        static final AtomicInteger constructorCalls = new AtomicInteger();

        private final Class<StrategyTarget> type;

        public StrategyInstantiator(Class<StrategyTarget> type) {
            constructorCalls.incrementAndGet();
            this.type = type;
        }

        @Override
        public StrategyTarget newInstance() {
            return new StrategyTarget(type);
        }
    }

    public static final class StrategyTarget {

        static final AtomicInteger constructorCalls = new AtomicInteger();

        private final Class<?> type;

        public StrategyTarget(Class<?> type) {
            constructorCalls.incrementAndGet();
            this.type = type;
        }

        Class<?> type() {
            return type;
        }
    }
}
