/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class HibernateValidatorTest {
    private Validator validator;

    @BeforeEach
    void setUp() {
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldBeValid() {
        Set<ConstraintViolation<Dto>> errors = this.validator.validate(Dto.createValid());
        assertThat(errors).isEmpty();
    }

    @Test
    void shouldBeInvalid() {
        Set<ConstraintViolation<Dto>> errors = this.validator.validate(Dto.createInvalid());
        assertThat(errors).extracting(ValidationError::of).containsExactlyInAnyOrder(
                new ValidationError("notBlank", "{jakarta.validation.constraints.NotBlank.message}"),
                new ValidationError("max", "{jakarta.validation.constraints.Max.message}"),
                new ValidationError("notNull", "{jakarta.validation.constraints.NotNull.message}"),
                new ValidationError("notEmpty", "{jakarta.validation.constraints.NotEmpty.message}"),
                new ValidationError("oNull", "{jakarta.validation.constraints.Null.message}"),
                new ValidationError("email", "{jakarta.validation.constraints.Email.message}"),
                new ValidationError("pattern", "{jakarta.validation.constraints.Pattern.message}"),
                new ValidationError("min", "{jakarta.validation.constraints.Min.message}"),
                new ValidationError("future", "{jakarta.validation.constraints.Future.message}"),
                new ValidationError("bFalse", "{jakarta.validation.constraints.AssertFalse.message}"),
                new ValidationError("bTrue", "{jakarta.validation.constraints.AssertTrue.message}"),
                new ValidationError("digits", "{jakarta.validation.constraints.Digits.message}"),
                new ValidationError("notEmptyString", "{jakarta.validation.constraints.NotEmpty.message}"),
                new ValidationError("notEmptyArray", "{jakarta.validation.constraints.NotEmpty.message}"),
                new ValidationError("notEmptyMap", "{jakarta.validation.constraints.NotEmpty.message}"),
                new ValidationError("negative", "{jakarta.validation.constraints.Negative.message}"),
                new ValidationError("range", "{org.hibernate.validator.constraints.Range.message}"),
                new ValidationError("size", "{jakarta.validation.constraints.Size.message}"),
                new ValidationError("positive", "{jakarta.validation.constraints.Positive.message}"),
                new ValidationError("ccNumber", "{org.hibernate.validator.constraints.CreditCardNumber.message}")
        );
    }

    private static final class ValidationError {
        private final String propertyPath;
        private final String messageTemplate;

        private ValidationError(java.lang.String propertyPath, java.lang.String messageTemplate) {
            this.propertyPath = propertyPath;
            this.messageTemplate = messageTemplate;
        }

        private static ValidationError of(ConstraintViolation<?> cv) {
            return new ValidationError(cv.getPropertyPath().toString(), cv.getMessageTemplate());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ValidationError that = (ValidationError) o;
            return Objects.equals(propertyPath, that.propertyPath) && Objects.equals(messageTemplate, that.messageTemplate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(propertyPath, messageTemplate);
        }
    }
}
