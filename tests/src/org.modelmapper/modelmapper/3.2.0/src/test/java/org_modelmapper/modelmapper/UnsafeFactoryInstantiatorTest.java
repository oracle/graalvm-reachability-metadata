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
import org.modelmapper.internal.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

public class UnsafeFactoryInstantiatorTest {
    @Test
    void allocatesInstanceWithoutInvokingConstructor() {
        ConstructorBypassedType.constructorCalls.set(0);

        UnsafeFactoryInstantiator<ConstructorBypassedType> instantiator =
            new UnsafeFactoryInstantiator<>(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(ConstructorBypassedType.class);
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
        assertThat(instance.initializedNumber).isZero();
        assertThat(instance.initializedText).isNull();
    }

    public static final class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        int initializedNumber;
        String initializedText;

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            initializedNumber = 42;
            initializedText = "initialized-by-constructor";
        }
    }
}
