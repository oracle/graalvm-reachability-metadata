/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.lang.annotation.Documented;
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

public class DefaultMessageInterpolatorTest {

    @Test
    void interpolatesPrimitiveArrayAnnotationAttribute() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().validate(new NumberBean()).iterator().next().getMessage())
                    .isEqualTo("allowed values [2, 4]");
        }
    }

    public static class NumberBean {
        @AllowedNumbers(numbers = {2, 4})
        private final int value = 3;
    }

    @Documented
    @Constraint(validatedBy = AllowedNumbersValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AllowedNumbers {
        String message() default "allowed values {numbers}";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        int[] numbers();
    }

    public static class AllowedNumbersValidator implements ConstraintValidator<AllowedNumbers, Integer> {
        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return false;
        }
    }
}
