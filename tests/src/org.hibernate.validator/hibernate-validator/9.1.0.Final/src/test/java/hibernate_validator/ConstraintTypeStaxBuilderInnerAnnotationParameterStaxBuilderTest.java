/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package hibernate_validator;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

public class ConstraintTypeStaxBuilderInnerAnnotationParameterStaxBuilderTest {
    @Test
    void xmlConstraintMappingBuildsConstraintWithArrayAndNestedAnnotationArrayParameters() {
        String mapping = """
                <?xml version="1.0" encoding="UTF-8"?>
                <constraint-mappings
                        xmlns="https://jakarta.ee/xml/ns/validation/mapping"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="https://jakarta.ee/xml/ns/validation/mapping
                                https://jakarta.ee/xml/ns/validation/validation-mapping-3.1.xsd"
                        version="3.1">
                    <bean class="hibernate_validator.ConstraintTypeStaxBuilderInnerAnnotationParameterStaxBuilderTest$XmlMappedOrder">
                        <field name="code">
                            <constraint annotation="hibernate_validator.ConstraintTypeStaxBuilderInnerAnnotationParameterStaxBuilderTest$AllowedCodes">
                                <message>code must match the configured XML choices</message>
                                <element name="allowed">
                                    <value>alpha</value>
                                    <value>bravo</value>
                                </element>
                                <element name="rules">
                                    <annotation>
                                        <element name="name">prefix</element>
                                        <element name="requiredParts">
                                            <value>a</value>
                                            <value>b</value>
                                        </element>
                                    </annotation>
                                    <annotation>
                                        <element name="name">suffix</element>
                                        <element name="requiredParts">
                                            <value>x</value>
                                            <value>y</value>
                                        </element>
                                    </annotation>
                                </element>
                            </constraint>
                        </field>
                    </bean>
                </constraint-mappings>
                """;
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        configuration.addMapping(new ByteArrayInputStream(mapping.getBytes(StandardCharsets.UTF_8)));

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<XmlMappedOrder>> violations = validator.validate(new XmlMappedOrder("charlie"));

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("code");
                assertThat(violation.getMessage()).isEqualTo("code must match the configured XML choices");
            });
        }
    }

    public static final class XmlMappedOrder {
        private final String code;

        public XmlMappedOrder(String code) {
            this.code = code;
        }
    }

    @Documented
    @Constraint(validatedBy = AllowedCodesValidator.class)
    @Target({ FIELD })
    @Retention(RUNTIME)
    public @interface AllowedCodes {
        String message() default "invalid code";

        Class<?>[] groups() default { };

        Class<? extends Payload>[] payload() default { };

        String[] allowed() default { };

        CodeRule[] rules() default { };
    }

    @Documented
    @Target({ })
    @Retention(RUNTIME)
    public @interface CodeRule {
        String name();

        String[] requiredParts() default { };
    }

    public static final class AllowedCodesValidator implements ConstraintValidator<AllowedCodes, String> {
        private List<String> allowedCodes;
        private List<String> ruleNames;
        private List<List<String>> requiredParts;

        @Override
        public void initialize(AllowedCodes constraintAnnotation) {
            allowedCodes = List.of(constraintAnnotation.allowed());
            ruleNames = List.of(constraintAnnotation.rules()).stream()
                    .map(CodeRule::name)
                    .toList();
            requiredParts = List.of(constraintAnnotation.rules()).stream()
                    .map(rule -> List.of(rule.requiredParts()))
                    .toList();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            assertThat(allowedCodes).containsExactly("alpha", "bravo");
            assertThat(ruleNames).containsExactly("prefix", "suffix");
            assertThat(requiredParts).containsExactly(List.of("a", "b"), List.of("x", "y"));

            return value == null || allowedCodes.contains(value);
        }
    }
}
