/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComputeConstraintValidatorClassInnerTypeWrapperTest {

    @Test
    void selectsValidatorForCovariantMultidimensionalArray() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().validate(new Bean(new String[][] {{"value"}}))).isEmpty();
            assertThat(factory.getValidator().validate(new Bean(new String[0][]))).hasSize(1);
        }
    }

    public static class Bean {
        @NonEmptyMatrix
        private final String[][] values;

        public Bean(String[][] values) {
            this.values = values;
        }
    }

    @Constraint(validatedBy = MatrixValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NonEmptyMatrix {
        String message() default "matrix must not be empty";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class MatrixValidator implements ConstraintValidator<NonEmptyMatrix, Object[][]> {
        @Override
        public boolean isValid(Object[][] value, ConstraintValidatorContext context) {
            return value != null && value.length > 0;
        }
    }
}
