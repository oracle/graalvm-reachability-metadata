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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GetMethodFromGetterNameCandidatesTest {
    private static final String NOT_BLANK_MESSAGE = "{jakarta.validation.constraints.NotBlank.message}";

    @Test
    void programmaticConstraintMappingFindsDeclaredGetter() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        ConstraintMapping mapping = configuration.createConstraintMapping();
        mapping.type(ProgrammaticallyMappedGetterForm.class)
                .getter("name")
                .constraint(new NotBlankDef());

        try (ValidatorFactory factory = configuration.addMapping(mapping).buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<ProgrammaticallyMappedGetterForm>> violations = validator.validate(
                    new ProgrammaticallyMappedGetterForm("")
            );

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("name");
                assertThat(violation.getMessageTemplate()).isEqualTo(NOT_BLANK_MESSAGE);
            });
        }
    }

    @Test
    void xmlConstraintMappingFindsInheritedGetter() {
        String mapping = """
                <?xml version="1.0" encoding="UTF-8"?>
                <constraint-mappings
                        xmlns="https://jakarta.ee/xml/ns/validation/mapping"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="https://jakarta.ee/xml/ns/validation/mapping
                                https://jakarta.ee/xml/ns/validation/validation-mapping-3.1.xsd"
                        version="3.1">
                    <bean class="hibernate_validator.GetMethodFromGetterNameCandidatesTest$XmlMappedGetterForm">
                        <getter name="name">
                            <constraint annotation="jakarta.validation.constraints.NotBlank"/>
                        </getter>
                    </bean>
                </constraint-mappings>
                """;
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        configuration.addMapping(new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8)));

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<XmlMappedGetterForm>> violations = validator.validate(new XmlMappedGetterForm(""));

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("name");
                assertThat(violation.getMessageTemplate()).isEqualTo(NOT_BLANK_MESSAGE);
            });
        }
    }

    private static final class ProgrammaticallyMappedGetterForm {
        private final String name;

        private ProgrammaticallyMappedGetterForm(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class BaseXmlMappedGetterForm {
        private final String name;

        public BaseXmlMappedGetterForm(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class XmlMappedGetterForm extends BaseXmlMappedGetterForm {
        public XmlMappedGetterForm(String name) {
            super(name);
        }
    }
}
