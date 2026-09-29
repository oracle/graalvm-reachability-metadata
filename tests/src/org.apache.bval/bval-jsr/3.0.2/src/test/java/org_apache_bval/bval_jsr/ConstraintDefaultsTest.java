/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstraintDefaultsTest {

    @Test
    void loadsBuiltInValidatorMappings() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().validate(new Bean(-1))).singleElement()
                    .satisfies(violation -> assertThat(violation.getConstraintDescriptor().getAnnotation())
                            .isInstanceOf(Positive.class));
        }
    }

    public static class Bean {
        @Positive
        private final int value;

        public Bean(int value) {
            this.value = value;
        }
    }
}
