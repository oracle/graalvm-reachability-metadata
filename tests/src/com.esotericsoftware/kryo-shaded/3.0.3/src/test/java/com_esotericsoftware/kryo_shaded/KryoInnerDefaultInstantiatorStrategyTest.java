/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.util.Util;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.InstantiatorStrategy;

public class KryoInnerDefaultInstantiatorStrategyTest {
    @Test
    void usesFallbackAfterReflectionCannotConstructLibraryType() {
        Object expected = new Object();
        RecordingFallbackStrategy fallbackStrategy = new RecordingFallbackStrategy(expected);
        Kryo.DefaultInstantiatorStrategy strategy = new Kryo.DefaultInstantiatorStrategy(fallbackStrategy);
        boolean originalIsAndroid = Util.isAndroid;

        try {
            Util.isAndroid = true;
            ObjectInstantiator instantiator = strategy.newInstantiatorOf(Registration.class);
            Object created = instantiator.newInstance();

            assertThat(fallbackStrategy.requestedType).isSameAs(Registration.class);
            assertThat(created).isSameAs(expected);
        } finally {
            Util.isAndroid = originalIsAndroid;
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
