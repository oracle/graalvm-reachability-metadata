/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsManagerTest {

    @Test
    void exposesAllAnnotationAttributesThroughConstraintDescriptor() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().getConstraintsForClass(Bean.class)
                    .getConstraintsForProperty("name").getConstraintDescriptors()).singleElement()
                    .satisfies(descriptor -> assertThat(descriptor.getAttributes())
                            .containsEntry("min", 2).containsEntry("max", 5));
        }
    }

    public static class Bean {
        @Size(min = 2, max = 5)
        private final String name = "abc";
    }
}
