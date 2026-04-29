/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.ObjectInstantiator;
import org.modelmapper.internal.objenesis.instantiator.basic.DelegatingToExoticInstantiator;

public class DelegatingToExoticInstantiatorTest {
    @Test
    void createsInstanceThroughNamedInstantiatorClass() {
        SampleExoticInstantiator.constructorArgument.set(null);

        TestDelegatingInstantiator<SampleTarget> instantiator = new TestDelegatingInstantiator<>(SampleTarget.class);
        SampleTarget instance = instantiator.newInstance();

        assertThat(SampleExoticInstantiator.constructorArgument).hasValue(SampleTarget.class);
        assertThat(instance).isNotNull();
        assertThat(instance.description).isEqualTo("created by exotic instantiator");
    }

    static final class SampleTarget {
        final String description;

        SampleTarget(String description) {
            this.description = description;
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
            if (type == SampleTarget.class) {
                return type.cast(new SampleTarget("created by exotic instantiator"));
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
