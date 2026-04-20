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
import org.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

public class UnsafeFactoryInstantiatorTest {

    @Test
    void createsInstancesWithoutRunningTheTargetConstructor() {
        ConstructorBypassedType.constructorCalls.set(0);

        UnsafeFactoryInstantiator<ConstructorBypassedType> instantiator =
            new UnsafeFactoryInstantiator<>(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
        Assertions.assertThat(instance.message).isNull();
        Assertions.assertThat(instance.number).isZero();
    }

    static class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;
        final int number;

        ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.message = "constructed";
            this.number = 42;
        }
    }
}
