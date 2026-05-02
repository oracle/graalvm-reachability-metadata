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
import org.springframework.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

public class UnsafeFactoryInstantiatorTest {

    @Test
    void createsInstanceWithoutInvokingAnyConstructor() {
        ConstructorBypassedType.constructorCalls.set(0);

        UnsafeFactoryInstantiator<ConstructorBypassedType> instantiator =
                new UnsafeFactoryInstantiator<>(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
        assertThat(instance.parentValue).isNull();
        assertThat(instance.childValue).isNull();
        assertThat(instance.number).isZero();
    }

    public static class ConstructorBypassedParent {

        String parentValue;

        public ConstructorBypassedParent() {
            this.parentValue = "created-by-parent";
        }
    }

    public static class ConstructorBypassedType extends ConstructorBypassedParent {

        static final AtomicInteger constructorCalls = new AtomicInteger();

        final String childValue;
        final int number;

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.childValue = "created-by-child";
            this.number = 42;
        }
    }
}
