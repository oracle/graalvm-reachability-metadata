/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

/** Verifies constructor-bypassing instantiation backed by ReflectionFactory. */
public class SunReflectionFactoryHelperTest {
    @Test
    void createsSerializationConstructorAndInstance() {
        SunReflectionFactoryInstantiator<ConstructorBypassedType> instantiator =
                new SunReflectionFactoryInstantiator<>(ConstructorBypassedType.class);

        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance.initialized).isFalse();
    }

    public static final class ConstructorBypassedType {
        private final boolean initialized;

        public ConstructorBypassedType() {
            initialized = true;
        }
    }
}
