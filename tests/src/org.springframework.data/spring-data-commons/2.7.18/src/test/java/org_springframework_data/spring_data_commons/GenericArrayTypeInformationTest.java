/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;

public class GenericArrayTypeInformationTest {

    @Test
    void resolvesGenericArrayPropertyTypeFromSpecializedOwner() {
        TypeInformation<?> propertyType = ClassTypeInformation.from(StringArrayHolder.class).getProperty("values");

        assertThat(propertyType).isNotNull();
        assertThat(propertyType.getType()).isEqualTo(String[].class);
        assertThat(propertyType.getComponentType()).isNotNull();
        assertThat(propertyType.getComponentType().getType()).isEqualTo(String.class);
    }

    private static class GenericArrayHolder<T> {

        private T[] values;
    }

    private static final class StringArrayHolder extends GenericArrayHolder<String> {
    }
}
