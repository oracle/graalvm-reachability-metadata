/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupSequence;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Bval_jsrTest {

    @Test
    void defaultProviderValidatesBeanGraphAndContainerElements() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Customer invalidCustomer = new Customer("", "not-an-email", 0, new Address(""),
                    List.of("priority", ""), "wrong-code");

            Set<ConstraintViolation<Customer>> violations = validator.validate(invalidCustomer);

            assertThat(factory.getClass().getName()).startsWith("org.apache.bval.");
            assertThat(violations).hasSize(6);
            assertViolation(violations, "name", NotBlank.class);
            assertViolation(violations, "email", Email.class);
            assertViolation(violations, "loyaltyPoints", Positive.class);
            assertViolation(violations, "address.street", NotBlank.class);
            assertThat(violations).anySatisfy((violation) -> {
                assertThat(violation.getPropertyPath().toString()).startsWith("tags[");
                assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(NotBlank.class);
                assertThat(violation.getInvalidValue()).isEqualTo("");
            });
            assertViolation(violations, "accountCode", StartsWith.class);
            assertThat(violations).allSatisfy((violation) -> assertThat(violation.getMessage()).isNotBlank());

            Customer validCustomer = new Customer("Ada", "ada@example.org", 12, new Address("Main Street"),
                    List.of("priority"), "ACC-42");
            assertThat(validator.validate(validCustomer)).isEmpty();
        }
    }

    @Test
    void propertyAndValueValidationReportConstraintDetails() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Customer customer = new Customer("Ada", "invalid", 12, new Address("Main Street"),
                    List.of("priority"), "ACC-42");

            Set<ConstraintViolation<Customer>> propertyViolations = validator.validateProperty(customer, "email");
            Set<ConstraintViolation<Customer>> valueViolations = validator.validateValue(Customer.class, "email",
                    "also-invalid");

            assertThat(propertyViolations).singleElement().satisfies((violation) -> {
                assertThat(violation.getRootBean()).isSameAs(customer);
                assertThat(violation.getLeafBean()).isSameAs(customer);
                assertThat(violation.getInvalidValue()).isEqualTo("invalid");
                assertThat(violation.getMessageTemplate()).isEqualTo("{jakarta.validation.constraints.Email.message}");
            });
            assertThat(valueViolations).singleElement().satisfies((violation) -> {
                assertThat(violation.getRootBean()).isNull();
                assertThat(violation.getRootBeanClass()).isEqualTo(Customer.class);
                assertThat(violation.getInvalidValue()).isEqualTo("also-invalid");
            });
        }
    }

    @Test
    void constraintMetadataDescribesConstrainedProperties() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            BeanDescriptor bean = factory.getValidator().getConstraintsForClass(Customer.class);
            PropertyDescriptor accountCode = bean.getConstraintsForProperty("accountCode");
            PropertyDescriptor address = bean.getConstraintsForProperty("address");

            assertThat(bean.isBeanConstrained()).isTrue();
            assertThat(bean.getConstrainedProperties())
                    .extracting(PropertyDescriptor::getPropertyName)
                    .containsExactlyInAnyOrder("name", "email", "loyaltyPoints", "address", "tags", "accountCode");
            assertThat(accountCode.getConstraintDescriptors()).singleElement().satisfies((descriptor) -> {
                assertThat(descriptor.getAnnotation()).isInstanceOf(StartsWith.class);
                assertThat(descriptor.getConstraintValidatorClasses())
                        .extracting(Class::getName)
                        .containsExactly(StartsWithValidator.class.getName());
                assertThat(descriptor.getAttributes()).containsEntry("prefix", "ACC-");
            });
            assertThat(address.isCascaded()).isTrue();
        }
    }

    @Test
    void executableValidatorChecksParametersAndReturnValue() throws NoSuchMethodException {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ReservationService service = new ReservationService();
            Method reserve = ReservationService.class.getMethod("reserve", String.class, int.class);
            ExecutableValidator executableValidator = factory.getValidator().forExecutables();

            Set<ConstraintViolation<ReservationService>> parameterViolations = executableValidator
                    .validateParameters(service, reserve, new Object[] {"", 0});
            assertThat(parameterViolations).hasSize(2);
            assertThat(parameterViolations)
                    .extracting((violation) -> violation.getConstraintDescriptor().getAnnotation().annotationType()
                            .getName())
                    .containsExactlyInAnyOrder(NotBlank.class.getName(), Min.class.getName());

            Object[] validArguments = {"ABCD", 1};
            assertThat(executableValidator.validateParameters(service, reserve, validArguments)).isEmpty();
            String result = service.reserve((String) validArguments[0], (Integer) validArguments[1]);
            assertThat(result).isEqualTo("ABCD");
            assertThat(executableValidator.validateReturnValue(service, reserve, result)).isEmpty();

            String shortResult = service.reserve("ABC", 1);
            assertThat(executableValidator.validateReturnValue(service, reserve, shortResult)).singleElement()
                    .satisfies((violation) -> {
                        assertThat(violation.getInvalidValue()).isEqualTo("ABC");
                        assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(Size.class);
                    });
        }
    }

    @Test
    void groupSequenceStopsAfterFirstFailingGroup() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<Registration>> basicViolations = validator
                    .validate(new Registration("", "123"), CompleteRegistration.class);
            assertThat(basicViolations).singleElement().satisfies((violation) -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("username");
                assertThat(violation.getConstraintDescriptor().getGroups()).containsExactly(BasicChecks.class);
            });

            Set<ConstraintViolation<Registration>> detailViolations = validator
                    .validate(new Registration("Ada", "123"), CompleteRegistration.class);
            assertThat(detailViolations).singleElement().satisfies((violation) -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("accessCode");
                assertThat(violation.getConstraintDescriptor().getGroups()).containsExactly(DetailChecks.class);
            });

            assertThat(validator.validate(new Registration("Ada", "12345"), CompleteRegistration.class)).isEmpty();
        }
    }

    private static void assertViolation(Set<? extends ConstraintViolation<?>> violations, String property,
            Class<?> annotationType) {
        assertThat(violations).anySatisfy((violation) -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo(property);
            assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType()).isEqualTo(annotationType);
        });
    }

    public static class Customer {

        @NotBlank
        private final String name;

        @Email
        private final String email;

        @Positive
        private final int loyaltyPoints;

        @Valid
        private final Address address;

        @Size(min = 1)
        private final List<@NotBlank String> tags;

        @StartsWith
        private final String accountCode;

        public Customer(String name, String email, int loyaltyPoints, Address address, List<String> tags,
                String accountCode) {
            this.name = name;
            this.email = email;
            this.loyaltyPoints = loyaltyPoints;
            this.address = address;
            this.tags = tags;
            this.accountCode = accountCode;
        }
    }

    public static class Address {

        @NotBlank
        private final String street;

        public Address(String street) {
            this.street = street;
        }
    }

    public static class ReservationService {

        @Size(min = 4)
        public String reserve(@NotBlank String code, @Min(1) int quantity) {
            return code.repeat(quantity);
        }
    }

    public interface BasicChecks {
    }

    public interface DetailChecks {
    }

    @GroupSequence({BasicChecks.class, DetailChecks.class})
    public interface CompleteRegistration {
    }

    public static class Registration {

        @NotBlank(groups = BasicChecks.class)
        private final String username;

        @Size(min = 5, groups = DetailChecks.class)
        private final String accessCode;

        public Registration(String username, String accessCode) {
            this.username = username;
            this.accessCode = accessCode;
        }
    }

    @Documented
    @Constraint(validatedBy = StartsWithValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE,
            ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StartsWith {

        String message() default "must start with {prefix}";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        String prefix() default "ACC-";
    }

    public static class StartsWithValidator implements ConstraintValidator<StartsWith, String> {

        private String prefix;

        @Override
        public void initialize(StartsWith annotation) {
            this.prefix = annotation.prefix();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.startsWith(this.prefix);
        }
    }
}
