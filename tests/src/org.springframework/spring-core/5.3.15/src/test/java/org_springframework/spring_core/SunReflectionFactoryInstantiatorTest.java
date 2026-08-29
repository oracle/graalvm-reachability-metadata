/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

/** Verifies ReflectionFactory-based constructor bypass. */
public class SunReflectionFactoryInstantiatorTest {
    @Test
    void createsObjectWithoutInvokingConstructor() {
        SunReflectionFactoryInstantiator<Fixture> instantiator =
                new SunReflectionFactoryInstantiator<>(Fixture.class);

        Fixture fixture = instantiator.newInstance();

        assertThat(fixture.value).isNull();
    }

    public static final class Fixture implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String value;

        public Fixture() {
            this.value = "initialized";
        }
    }
}
