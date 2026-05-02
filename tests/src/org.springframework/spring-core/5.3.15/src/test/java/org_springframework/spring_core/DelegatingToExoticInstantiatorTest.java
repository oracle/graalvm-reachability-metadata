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
import org.springframework.objenesis.instantiator.basic.DelegatingToExoticInstantiator;

public class DelegatingToExoticInstantiatorTest {

    @Test
    void loadsDelegatedInstantiatorByNameAndCreatesItReflectively() {
        DelegatedType.constructorCalls.set(0);
        ObjectInstantiator<DelegatedType> instantiator = new TestDelegatingInstantiator<>(
                DelegatedObjectInstantiator.class.getName(),
                DelegatedType.class
        );

        DelegatedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance.value()).isEqualTo("created by delegated instantiator");
        assertThat(DelegatedType.constructorCalls).hasValue(1);
    }

    private static class TestDelegatingInstantiator<T> extends DelegatingToExoticInstantiator<T> {
        TestDelegatingInstantiator(String instantiatorClassName, Class<T> type) {
            super(instantiatorClassName, type);
        }
    }

    public static class DelegatedObjectInstantiator<T> implements ObjectInstantiator<T> {
        private final Class<T> type;

        public DelegatedObjectInstantiator(Class<T> type) {
            this.type = type;
        }

        @Override
        public T newInstance() {
            assertThat(type).isEqualTo(DelegatedType.class);
            return type.cast(new DelegatedType());
        }
    }

    public static class DelegatedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        private final String value;

        public DelegatedType() {
            constructorCalls.incrementAndGet();
            this.value = "created by delegated instantiator";
        }

        String value() {
            return value;
        }
    }
}
