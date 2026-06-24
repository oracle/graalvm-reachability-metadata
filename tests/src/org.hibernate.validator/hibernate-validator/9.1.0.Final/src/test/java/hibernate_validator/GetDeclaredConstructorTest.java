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
import jakarta.validation.executable.ExecutableValidator;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.defs.NotBlankDef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GetDeclaredConstructorTest {
    @Test
    void programmaticConstructorMappingFindsDeclaredConstructor() throws NoSuchMethodException {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        ConstraintMapping mapping = configuration.createConstraintMapping();
        mapping.type(ConstructorValidatedBean.class)
                .constructor(String.class)
                .parameter(0)
                .constraint(new NotBlankDef().message("constructor value must not be blank"));
        configuration.addMapping(mapping);

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            Constructor<ConstructorValidatedBean> constructor = ConstructorValidatedBean.class.getConstructor(
                    String.class
            );
            ExecutableValidator executableValidator = validator.forExecutables();

            Set<ConstraintViolation<ConstructorValidatedBean>> violations = executableValidator
                    .validateConstructorParameters(
                            constructor,
                            new Object[] { "" }
                    );

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getRootBeanClass()).isEqualTo(ConstructorValidatedBean.class);
                assertThat(violation.getMessageTemplate()).isEqualTo("constructor value must not be blank");
                assertThat(violation.getMessage()).isEqualTo("constructor value must not be blank");
            });
        }
    }

    public static final class ConstructorValidatedBean {
        private final String value;

        public ConstructorValidatedBean(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
