/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.InstantiatorStrategy;

public class KryoInnerDefaultInstantiatorStrategyTest {
    @Test
    void usesFallbackInstantiatorForNonStaticMemberClass() {
        NonStaticMember expected = new NonStaticMember("fallback-created");
        RecordingFallbackStrategy fallbackStrategy = new RecordingFallbackStrategy(expected);
        Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy(fallbackStrategy);

        ObjectInstantiator instantiator = strategy.newInstantiatorOf(NonStaticMember.class);
        Object created = instantiator.newInstance();

        assertThat(fallbackStrategy.requestedType).isSameAs(NonStaticMember.class);
        assertThat(created).isSameAs(expected);
        assertThat(((NonStaticMember) created).value).isEqualTo("fallback-created");
    }

    private class NonStaticMember {
        final String value;

        NonStaticMember(String value) {
            this.value = value;
        }
    }

    private static class RecordingFallbackStrategy implements InstantiatorStrategy {
        private final Object instance;
        private Class<?> requestedType;

        RecordingFallbackStrategy(Object instance) {
            this.instance = instance;
        }

        @Override
        public ObjectInstantiator newInstantiatorOf(Class type) {
            requestedType = type;
            return () -> instance;
        }
    }
}
