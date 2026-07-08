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
import jakarta.validation.constraints.Pattern;

import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GetAnnotationAttributeTest {
    @Test
    void repeatedBuiltinConstraintContainerIsExpandedDuringValidation() {
        try (ValidatorFactory factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<PatternListForm>> violations = validator.validate(new PatternListForm("abc"));

            assertThat(violations).hasSize(2);
            assertThat(violations)
                    .allSatisfy(violation -> assertThat(violation.getPropertyPath()).hasToString("code"))
                    .extracting(ConstraintViolation::getMessageTemplate)
                    .containsExactlyInAnyOrder(
                            "{jakarta.validation.constraints.Pattern.message}",
                            "{jakarta.validation.constraints.Pattern.message}"
                    );
        }
    }
}

final class PatternListForm {
    @Pattern.List({
            @Pattern(regexp = "\\d+"),
            @Pattern(regexp = "[A-Z]+")
    })
    private final String code;

    PatternListForm(String code) {
        this.code = code;
    }
}
