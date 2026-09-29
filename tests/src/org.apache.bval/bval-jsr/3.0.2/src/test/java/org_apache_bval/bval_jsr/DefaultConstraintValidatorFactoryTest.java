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

public class DefaultConstraintValidatorFactoryTest {

    @Test
    void constructsCustomConstraintValidator() {
        try (ValidatorFactory factory = ValidationTestSupport.factory()) {
            assertThat(factory.getValidator().validate(new Bean("bad"))).hasSize(1);
            assertThat(factory.getValidator().validate(new Bean("ok"))).isEmpty();
        }
    }

    public static class Bean {
        @IsOk
        private final String value;

        public Bean(String value) {
            this.value = value;
        }
    }

    @Constraint(validatedBy = IsOkValidator.class)
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IsOk {
        String message() default "must be ok";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class IsOkValidator implements ConstraintValidator<IsOk, String> {
        public IsOkValidator() {
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return "ok".equals(value);
        }
    }
}
