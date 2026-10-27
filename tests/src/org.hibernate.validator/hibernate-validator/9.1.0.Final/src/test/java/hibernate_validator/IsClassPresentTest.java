/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hibernate_validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;

import org.joda.time.Instant;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class IsClassPresentTest {
    @Test
    void defaultValidatorFactoryDetectsOptionalConstraintImplementations() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<Form>> violations = validator.validate(new Form("", new Instant(0)));

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("name", "expiresAt");
            ConstraintViolation<Form> nameViolation = violations.stream()
                    .filter(violation -> violation.getPropertyPath().toString().equals("name"))
                    .findFirst()
                    .orElseThrow();
            assertThat(nameViolation.getMessage()).isEqualTo("name is required");
        }
    }

    private static final class Form {
        @NotBlank(message = "${validatedValue == '' ? 'name is required' : 'unexpected value'}")
        private final String name;

        @Future
        private final Instant expiresAt;

        private Form(String name, Instant expiresAt) {
            this.name = name;
            this.expiresAt = expiresAt;
        }
    }
}
