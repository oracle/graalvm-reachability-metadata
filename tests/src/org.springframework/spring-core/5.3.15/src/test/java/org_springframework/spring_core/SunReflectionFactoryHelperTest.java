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

/** Verifies creation of a serialization constructor through ReflectionFactory. */
public class SunReflectionFactoryHelperTest {
    @Test
    void createsSerializationConstructor() {
        SunReflectionFactoryInstantiator<ConstructorBypassedType> instantiator =
                new SunReflectionFactoryInstantiator<>(ConstructorBypassedType.class);

        assertThat(instantiator).isNotNull();
    }

    public static final class ConstructorBypassedType {
        public ConstructorBypassedType() { }
    }
}
