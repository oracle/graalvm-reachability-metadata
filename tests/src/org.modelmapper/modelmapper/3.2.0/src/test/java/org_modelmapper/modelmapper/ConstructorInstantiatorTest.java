/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.basic.ConstructorInstantiator;

public class ConstructorInstantiatorTest {
    @Test
    void invokesTargetNoArgumentConstructor() {
        ConstructorTarget.constructorCalls.set(0);

        ConstructorInstantiator<ConstructorTarget> instantiator =
            new ConstructorInstantiator<>(ConstructorTarget.class);
        ConstructorTarget instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance.message).isEqualTo("constructed by constructor instantiator");
        assertThat(ConstructorTarget.constructorCalls).hasValue(1);
    }

    public static final class ConstructorTarget {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;

        public ConstructorTarget() {
            constructorCalls.incrementAndGet();
            this.message = "constructed by constructor instantiator";
        }
    }
}
