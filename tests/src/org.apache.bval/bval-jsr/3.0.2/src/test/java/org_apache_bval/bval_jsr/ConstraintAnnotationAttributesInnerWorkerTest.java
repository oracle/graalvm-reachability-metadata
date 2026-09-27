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

public class ConstraintAnnotationAttributesInnerWorkerTest {

    @Test
    void readsStandardConstraintMembers() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().getConstraintsForClass(Bean.class)
                    .getConstraintsForProperty("value").getConstraintDescriptors()).singleElement()
                    .satisfies(descriptor -> {
                        assertThat(descriptor.getAttributes()).containsEntry("min", 3);
                        assertThat(descriptor.getAnnotation()).isInstanceOf(Size.class);
                    });
        }
    }

    public static class Bean {
        @Size(min = 3)
        private final String value = "ok";
    }
}
