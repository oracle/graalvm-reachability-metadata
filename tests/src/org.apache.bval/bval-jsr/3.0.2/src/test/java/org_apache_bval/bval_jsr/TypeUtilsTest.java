/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.util.List;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.PropertyDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeUtilsTest {

    @Test
    void resolvesGenericArrayPropertyType() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            PropertyDescriptor property = factory.getValidator().getConstraintsForClass(Bean.class)
                    .getConstraintsForProperty("values");

            assertThat(property.getElementClass()).isEqualTo(List[].class);
        }
    }

    public static class Bean {
        @Size(min = 1)
        private final List<String>[] values;

        @SuppressWarnings("unchecked")
        public Bean() {
            values = new List[] {List.of("value")};
        }
    }
}
