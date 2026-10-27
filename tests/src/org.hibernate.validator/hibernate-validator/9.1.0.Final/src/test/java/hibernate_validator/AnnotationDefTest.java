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
import org.hibernate.validator.cfg.AnnotationDef;
import org.hibernate.validator.cfg.ConstraintDef;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationDefTest {
    @Test
    void programmaticConstraintMappingAcceptsAnnotationArrayParameters() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        ConstraintMapping mapping = configuration.createConstraintMapping();
        mapping.type(ProgrammaticOrder.class)
                .field("code")
                .constraint(new RequiresPartsDef()
                        .part(new NamedPartDef().name("prefix"))
                        .part(new NamedPartDef().name("suffix"))
                        .message("code must contain every configured part"));
        configuration.addMapping(mapping);

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<ProgrammaticOrder>> violations = validator.validate(
                    new ProgrammaticOrder("prefix-only")
            );

            assertThat(violations).singleElement().satisfies(violation -> {
                assertThat(violation.getPropertyPath()).hasToString("code");
                assertThat(violation.getMessage()).isEqualTo("code must contain every configured part");
            });
        }
    }

    public static final class ProgrammaticOrder {
        private final String code;

        public ProgrammaticOrder(String code) {
            this.code = code;
        }
    }

    public static final class RequiresPartsDef extends ConstraintDef<RequiresPartsDef, RequiresParts> {
        public RequiresPartsDef() {
            super(RequiresParts.class);
        }

        public RequiresPartsDef part(NamedPartDef part) {
            return addAnnotationAsParameter("value", part);
        }
    }

    public static final class NamedPartDef extends AnnotationDef<NamedPartDef, NamedPart> {
        public NamedPartDef() {
            super(NamedPart.class);
        }

        public NamedPartDef name(String name) {
            return addParameter("name", name);
        }
    }

    @Documented
    @Constraint(validatedBy = RequiresPartsValidator.class)
    @Target({ FIELD })
    @Retention(RUNTIME)
    public @interface RequiresParts {
        String message() default "invalid code";

        Class<?>[] groups() default { };

        Class<? extends Payload>[] payload() default { };

        NamedPart[] value() default { };
    }

    @Documented
    @Target({ })
    @Retention(RUNTIME)
    public @interface NamedPart {
        String name();
    }

    public static final class RequiresPartsValidator implements ConstraintValidator<RequiresParts, String> {
        private List<String> expectedParts;

        @Override
        public void initialize(RequiresParts constraintAnnotation) {
            expectedParts = Arrays.stream(constraintAnnotation.value())
                    .map(NamedPart::name)
                    .toList();
            assertThat(expectedParts).containsExactly("prefix", "suffix");
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || expectedParts.stream().allMatch(value::contains);
        }
    }
}
