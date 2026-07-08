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

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.defs.NotBlankDef;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GetDeclaredMethodHandleTest {
    @Test
    void programmaticConstraintMappingBuildsConstraintAnnotationDescriptor() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        ConstraintMapping mapping = configuration.createConstraintMapping();
        mapping.type(Profile.class)
                .field("name")
                .constraint(new NotBlankDef());

        try (ValidatorFactory factory = configuration.addMapping(mapping).buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<Profile>> violations = validator.validate(new Profile(""));

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("name");
                assertThat(violation.getMessageTemplate()).isEqualTo("{jakarta.validation.constraints.NotBlank.message}");
            });
        }
    }

    private static final class Profile {
        private final String name;

        private Profile(String name) {
            this.name = name;
        }
    }
}
