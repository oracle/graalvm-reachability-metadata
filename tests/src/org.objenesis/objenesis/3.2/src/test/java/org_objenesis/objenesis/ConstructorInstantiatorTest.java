/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.basic.ConstructorInstantiator;

public class ConstructorInstantiatorTest {

    @Test
    void invokesTheTargetNoArgConstructorToCreateANewInstance() {
        ConstructorTarget.constructorCalls.set(0);

        ConstructorInstantiator<ConstructorTarget> instantiator =
            new ConstructorInstantiator<>(ConstructorTarget.class);
        ConstructorTarget instance = instantiator.newInstance();

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(instance.message).isEqualTo("constructed");
        Assertions.assertThat(ConstructorTarget.constructorCalls).hasValue(1);
    }

    public static final class ConstructorTarget {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;

        public ConstructorTarget() {
            constructorCalls.incrementAndGet();
            this.message = "constructed";
        }
    }
}
