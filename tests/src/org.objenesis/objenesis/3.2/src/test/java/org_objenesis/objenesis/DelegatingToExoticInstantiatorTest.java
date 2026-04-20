/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.instantiator.basic.DelegatingToExoticInstantiator;

public class DelegatingToExoticInstantiatorTest {

    @Test
    void loadsAndConstructsTheNamedInstantiatorClass() {
        SampleExoticInstantiator.constructorArgument.set(null);

        TestDelegatingInstantiator<MarkerType> instantiator = new TestDelegatingInstantiator<>(MarkerType.class);
        MarkerType instance = instantiator.newInstance();

        Assertions.assertThat(SampleExoticInstantiator.constructorArgument).hasValue(MarkerType.class);
        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(instance.message).isEqualTo("created-by-test-exotic");
    }

    static final class MarkerType {
        final String message;

        MarkerType(String message) {
            this.message = message;
        }
    }

    public static final class SampleExoticInstantiator<T> implements ObjectInstantiator<T> {
        static final AtomicReference<Class<?>> constructorArgument = new AtomicReference<>();

        private final Class<T> type;

        public SampleExoticInstantiator(Class<T> type) {
            this.type = type;
            constructorArgument.set(type);
        }

        @Override
        public T newInstance() {
            if (type == MarkerType.class) {
                return type.cast(new MarkerType("created-by-test-exotic"));
            }
            throw new IllegalStateException("Unexpected type: " + type.getName());
        }
    }

    static final class TestDelegatingInstantiator<T> extends DelegatingToExoticInstantiator<T> {
        TestDelegatingInstantiator(Class<T> type) {
            super(SampleExoticInstantiator.class.getName(), type);
        }
    }
}
