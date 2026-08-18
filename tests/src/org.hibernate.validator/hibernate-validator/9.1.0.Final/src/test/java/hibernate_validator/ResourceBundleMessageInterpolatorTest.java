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
import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceBundleMessageInterpolatorTest {
    @Test
    void defaultValidatorFactoryUsesResourceBundleMessageInterpolator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<Form>> violations = validator.validate(new Form(""));

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("name");
                assertThat(violation.getMessage()).isEqualTo("name is required");
            });
        }
    }

    @Test
    void explicitInterpolatorEvaluatesExpressionLanguageMessages() {
        try (ValidatorFactory factory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ResourceBundleMessageInterpolator())
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<Form>> violations = validator.validate(new Form(""));

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("name");
                assertThat(violation.getMessage()).isEqualTo("name is required");
            });
        }
    }

    private static final class Form {
        @NotBlank(message = "${validatedValue == '' ? 'name is required' : 'unexpected value'}")
        private final String name;

        private Form(String name) {
            this.name = name;
        }
    }
}
