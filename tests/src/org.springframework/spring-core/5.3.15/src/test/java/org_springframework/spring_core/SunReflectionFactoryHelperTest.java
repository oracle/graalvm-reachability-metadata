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
import org.springframework.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

public class SunReflectionFactoryHelperTest {

    @Test
    void createsInstanceWithoutRunningTargetConstructor() {
        ConstructorBypassedType.constructorCalls.set(0);
        ObjectInstantiator<ConstructorBypassedType> instantiator = new SunReflectionFactoryInstantiator<>(
                ConstructorBypassedType.class
        );

        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
        assertThat(instance.message).isNull();
        assertThat(instance.number).isZero();
    }

    public static class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String message;
        final int number;

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.message = "constructed";
            this.number = 42;
        }
    }
}
