/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.MappingException;
import org.modelmapper.internal.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

public class SunReflectionFactoryHelperTest {
    @Test
    void createsSerializationConstructorBackedInstantiator() {
        SunReflectionFactoryInstantiator<MappingException> instantiator =
            new SunReflectionFactoryInstantiator<>(MappingException.class);

        MappingException instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(MappingException.class);
    }
}
