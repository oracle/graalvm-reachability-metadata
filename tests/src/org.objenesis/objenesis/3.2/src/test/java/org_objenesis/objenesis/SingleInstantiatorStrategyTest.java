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
import org.objenesis.strategy.SingleInstantiatorStrategy;

public class SingleInstantiatorStrategyTest {

    @Test
    void resolvesAndInvokesTheConfiguredInstantiatorConstructor() {
        RecordingInstantiator.constructorArgument.set(null);

        SingleInstantiatorStrategy strategy = new SingleInstantiatorStrategy(RecordingInstantiator.class);
        ObjectInstantiator<SampleType> instantiator = strategy.newInstantiatorOf(SampleType.class);
        SampleType instance = instantiator.newInstance();

        Assertions.assertThat(RecordingInstantiator.constructorArgument).hasValue(SampleType.class);
        Assertions.assertThat(instantiator).isInstanceOf(RecordingInstantiator.class);
        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(instance.message).isEqualTo("created-by-single-strategy");
    }

    public static final class SampleType {
        final String message;

        SampleType(String message) {
            this.message = message;
        }
    }

    public static final class RecordingInstantiator<T> implements ObjectInstantiator<T> {
        static final AtomicReference<Class<?>> constructorArgument = new AtomicReference<>();

        private final Class<T> type;

        public RecordingInstantiator(Class<T> type) {
            this.type = type;
            constructorArgument.set(type);
        }

        @Override
        public T newInstance() {
            if (type == SampleType.class) {
                return type.cast(new SampleType("created-by-single-strategy"));
            }
            throw new IllegalStateException("Unexpected type: " + type.getName());
        }
    }
}
