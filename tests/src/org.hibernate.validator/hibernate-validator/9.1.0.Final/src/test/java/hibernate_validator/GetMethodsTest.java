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

import org.hibernate.validator.group.GroupSequenceProvider;
import org.hibernate.validator.spi.group.DefaultGroupSequenceProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GetMethodsTest {
    @Test
    void defaultGroupSequenceProviderIsDiscoveredDuringValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<Registration>> violations = validator.validate(new Registration(""));

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("name");
                assertThat(violation.getMessage()).isEqualTo("name is required");
            });
        }
    }

    @GroupSequenceProvider(RegistrationGroupSequenceProvider.class)
    public static final class Registration {
        @NotBlank(message = "name is required")
        private final String name;

        public Registration(String name) {
            this.name = name;
        }
    }

    public static final class RegistrationGroupSequenceProvider
            implements DefaultGroupSequenceProvider<Registration> {
        @Override
        public List<Class<?>> getValidationGroups(Registration registration) {
            return List.of(Registration.class);
        }
    }
}
