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
import org.springframework.objenesis.instantiator.basic.DelegatingToExoticInstantiator;

/** Verifies delegation to an instantiator selected by its public class name. */
public class DelegatingToExoticInstantiatorTest {
    @Test
    void constructsAndUsesNamedInstantiator() {
        FixtureInstantiator.constructedType = null;
        NamedInstantiator<SampleValue> instantiator = new NamedInstantiator<>(SampleValue.class);

        SampleValue value = instantiator.newInstance();

        assertThat(FixtureInstantiator.constructedType).isEqualTo(SampleValue.class);
        assertThat(value.getText()).isEqualTo("created by delegated instantiator");
    }

    public static final class NamedInstantiator<T> extends DelegatingToExoticInstantiator<T> {
        public NamedInstantiator(Class<T> type) {
            super(FixtureInstantiator.class.getName(), type);
        }
    }

    public static final class FixtureInstantiator<T> implements ObjectInstantiator<T> {
        private static Class<?> constructedType;

        private final Class<T> type;

        public FixtureInstantiator(Class<T> type) {
            this.type = type;
            constructedType = type;
        }

        @Override
        public T newInstance() {
            return this.type.cast(new SampleValue("created by delegated instantiator"));
        }
    }

    public static final class SampleValue {
        private final String text;

        public SampleValue(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }
    }
}
