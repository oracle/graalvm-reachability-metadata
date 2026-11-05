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
    void resolvesGenericArrayPropertyAgainstConcreteComponentType() {
        TypeInformation<?> values = ClassTypeInformation.from(Names.class).getRequiredProperty("values");

        assertThat(values.getType()).isEqualTo(String[].class);
        assertThat(values.isCollectionLike()).isTrue();
        assertThat(values.getRequiredComponentType().getType()).isEqualTo(String.class);
    }

    public static class Batch<T> {

        private T[] values;

        public T[] getValues() {
            return values;
        }

        public void setValues(T[] values) {
            this.values = values;
        }
    }

    public static class Names extends Batch<String> {
    }
}
