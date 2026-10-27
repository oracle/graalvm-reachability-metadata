/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * XML constraint mappings loaded through `Configuration.addMapping(InputStream)`: field, getter,
 * class-level, cascaded and container element constraints declared in a Jakarta Validation
 * mapping document, `ignore-annotations` overriding a field's annotation, a constraint
 * definition whose validator is bound only in XML, group membership, method parameter
 * constraints, the resulting constraint metadata, and the error raised for a mapping that names
 * a missing field.
 *
 * A payroll provider imports employee records from many client companies. The compliance team
 * owns the import rules as an XML document that is reviewed and deployed separately from the
 * code, so the import service loads that document when it boots and checks every record against
 * it before the record reaches the payroll run.
 */
public class PayrollImportXmlMappingTest {

    private static final String PAYROLL_RULES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <constraint-mappings
                    xmlns="https://jakarta.ee/xml/ns/validation/mapping"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="https://jakarta.ee/xml/ns/validation/mapping
                        https://jakarta.ee/xml/ns/validation/validation-mapping-3.0.xsd"
                    version="3.0">
                <default-package>org_hibernate_validator.hibernate_validator</default-package>
                <bean class="PayrollImportXmlMappingTest$Employee" ignore-annotations="false">
                    <class>
                        <constraint annotation="PayrollImportXmlMappingTest$PayPeriodMatchesFrequency">
                            <message>pay period does not match the pay frequency</message>
                        </constraint>
                    </class>
                    <field name="employeeId">
                        <constraint annotation="jakarta.validation.constraints.NotNull"/>
                        <constraint annotation="jakarta.validation.constraints.Pattern">
                            <message>employee id must be two letters followed by six digits</message>
                            <element name="regexp">[A-Z]{2}[0-9]{6}</element>
                        </constraint>
                    </field>
                    <field name="displayName" ignore-annotations="true">
                        <constraint annotation="jakarta.validation.constraints.Size">
                            <element name="min">2</element>
                            <element name="max">40</element>
                        </constraint>
                    </field>
                    <field name="email">
                        <constraint annotation="jakarta.validation.constraints.Email"/>
                    </field>
                    <field name="taxNumber">
                        <constraint annotation="jakarta.validation.constraints.NotBlank">
                            <message>a tax number is required to onboard an employee</message>
                            <groups>
                                <value>PayrollImportXmlMappingTest$Onboarding</value>
                            </groups>
                        </constraint>
                    </field>
                    <field name="address">
                        <valid/>
                    </field>
                    <field name="costCentres">
                        <container-element-type type-argument-index="0">
                            <constraint annotation="jakarta.validation.constraints.NotBlank"/>
                        </container-element-type>
                    </field>
                    <getter name="annualSalary">
                        <constraint annotation="jakarta.validation.constraints.DecimalMin">
                            <message>annual salary must be at least {value}</message>
                            <element name="value">12000</element>
                        </constraint>
                    </getter>
                </bean>
                <bean class="PayrollImportXmlMappingTest$Address">
                    <field name="city">
                        <constraint annotation="jakarta.validation.constraints.NotBlank"/>
                    </field>
                    <field name="postalCode">
                        <constraint annotation="jakarta.validation.constraints.Pattern">
                            <message>postal code must have five digits</message>
                            <element name="regexp">[0-9]{5}</element>
                        </constraint>
                    </field>
                </bean>
                <bean class="PayrollImportXmlMappingTest$PayrollService">
                    <method name="applyRaise">
                        <parameter type="PayrollImportXmlMappingTest$Employee">
                            <constraint annotation="jakarta.validation.constraints.NotNull"/>
                        </parameter>
                        <parameter type="java.math.BigDecimal">
                            <constraint annotation="jakarta.validation.constraints.Positive"/>
                            <constraint annotation="jakarta.validation.constraints.DecimalMax">
                                <message>a raise may not exceed {value} percent</message>
                                <element name="value">20</element>
                            </constraint>
                        </parameter>
                    </method>
                </bean>
                <constraint-definition annotation="PayrollImportXmlMappingTest$PayPeriodMatchesFrequency">
                    <validated-by include-existing-validators="false">
                        <value>PayrollImportXmlMappingTest$PayPeriodMatchesFrequencyValidator</value>
                    </validated-by>
                </constraint-definition>
            </constraint-mappings>
            """;

    private static final String RULES_FOR_MISSING_FIELD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <constraint-mappings xmlns="https://jakarta.ee/xml/ns/validation/mapping" version="3.0">
                <default-package>org_hibernate_validator.hibernate_validator</default-package>
                <bean class="PayrollImportXmlMappingTest$Employee">
                    <field name="bonusPercentage">
                        <constraint annotation="jakarta.validation.constraints.Max">
                            <element name="value">50</element>
                        </constraint>
                    </field>
                </bean>
            </constraint-mappings>
            """;

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void loadComplianceRules() throws IOException {
        factory = buildFactory(PAYROLL_RULES);
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsARecordThatSatisfiesTheXmlRules() {
        Employee employee = validEmployee();

        assertTrue(validator.validate(employee).isEmpty());
        assertTrue(validator.validate(employee, Onboarding.class).isEmpty());
    }

    @Test
    void reportsFieldAndGetterViolationsDeclaredInXml() {
        Employee employee = validEmployee();
        employee.employeeId = "4711";
        employee.email = "payroll at example.com";
        employee.monthlySalary = new BigDecimal("850.00");

        Map<String, String> messages = messagesByPath(validator.validate(employee));

        assertEquals(Map.of(
                "employeeId", "employee id must be two letters followed by six digits",
                "email", "must be a well-formed email address",
                "annualSalary", "annual salary must be at least 12000"), messages);
    }

    @Test
    void xmlOverridesTheAnnotationOnAnIgnoredField() {
        Employee employee = validEmployee();
        employee.displayName = "Annabel Lee";

        assertTrue(validator.validate(employee).isEmpty(),
                "the @Size(max = 3) annotation in code must be ignored");

        employee.displayName = "A";
        Map<String, String> messages = messagesByPath(validator.validate(employee));

        assertEquals(Map.of("displayName", "size must be between 2 and 40"), messages);
    }

    @Test
    void cascadesIntoTheAddressAndChecksEveryCostCentre() {
        Employee employee = validEmployee();
        employee.address = new Address(" ", "1011");
        employee.costCentres = List.of("CC-100", "");

        Map<String, String> messages = messagesByPath(validator.validate(employee));

        assertEquals(Map.of(
                "address.city", "must not be blank",
                "address.postalCode", "postal code must have five digits",
                "costCentres[1].<list element>", "must not be blank"), messages);
    }

    @Test
    void appliesTheClassLevelConstraintWhoseValidatorIsBoundInXml() {
        Employee employee = validEmployee();
        employee.payFrequency = "BIWEEKLY";
        employee.payPeriodDays = 30;

        Set<ConstraintViolation<Employee>> violations = validator.validate(employee);

        assertEquals(1, violations.size());
        ConstraintViolation<Employee> violation = violations.iterator().next();
        assertEquals("", violation.getPropertyPath().toString());
        assertEquals("pay period does not match the pay frequency", violation.getMessage());
        assertEquals(PayPeriodMatchesFrequency.class,
                violation.getConstraintDescriptor().getAnnotation().annotationType());

        employee.payPeriodDays = 14;
        assertTrue(validator.validate(employee).isEmpty());
    }

    @Test
    void checksTheTaxNumberOnlyWhenOnboarding() {
        Employee employee = validEmployee();
        employee.taxNumber = "";

        assertTrue(validator.validate(employee).isEmpty());

        Map<String, String> messages = messagesByPath(validator.validate(employee, Onboarding.class));
        assertEquals(Map.of("taxNumber", "a tax number is required to onboard an employee"), messages);
    }

    @Test
    void validatesRaiseParametersDeclaredInXml() throws NoSuchMethodException {
        PayrollService service = new PayrollService();
        Method applyRaise = PayrollService.class.getMethod("applyRaise", Employee.class, BigDecimal.class);
        Employee employee = validEmployee();

        assertTrue(validator.forExecutables()
                .validateParameters(service, applyRaise, new Object[] {employee, new BigDecimal("4.5")})
                .isEmpty());
        assertEquals(0, new BigDecimal("3135").compareTo(service.applyRaise(employee, new BigDecimal("4.5"))));

        Map<Integer, String> tooGenerous = messagesByParameterIndex(validator.forExecutables()
                .validateParameters(service, applyRaise, new Object[] {employee, new BigDecimal("25")}));
        assertEquals(Map.of(1, "a raise may not exceed 20 percent"), tooGenerous);

        Map<Integer, String> cut = messagesByParameterIndex(validator.forExecutables()
                .validateParameters(service, applyRaise, new Object[] {null, new BigDecimal("-3")}));
        assertEquals(Map.of(0, "must not be null", 1, "must be greater than 0"), cut);
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void exposesXmlDeclaredConstraintsAsMetadata() {
        BeanDescriptor employee = validator.getConstraintsForClass(Employee.class);

        assertTrue(employee.isBeanConstrained());
        assertEquals(Set.of(PayPeriodMatchesFrequency.class), employee.getConstraintDescriptors().stream()
                .map(descriptor -> descriptor.getAnnotation().annotationType())
                .collect(Collectors.toSet()));

        Set<ConstraintDescriptor<?>> displayName = employee.getConstraintsForProperty("displayName")
                .getConstraintDescriptors();
        assertEquals(1, displayName.size());
        ConstraintDescriptor<?> size = displayName.iterator().next();
        assertEquals(Size.class, size.getAnnotation().annotationType());
        assertEquals(2, size.getAttributes().get("min"));
        assertEquals(40, size.getAttributes().get("max"));

        assertTrue(employee.getConstraintsForProperty("address").isCascaded());
        assertTrue(employee.getConstraintsForProperty("annualSalary").hasConstraints());
        assertEquals(2, validator.getConstraintsForClass(PayrollService.class)
                .getConstraintsForMethod("applyRaise", Employee.class, BigDecimal.class)
                .getParameterDescriptors().get(1).getConstraintDescriptors().size());
    }

    @Test
    void rejectsAMappingThatNamesAMissingField() {
        ValidationException failure = assertThrows(ValidationException.class,
                () -> buildFactory(RULES_FOR_MISSING_FIELD).close());

        assertTrue(failure.getMessage().contains("bonusPercentage"), failure.getMessage());
    }

    private static ValidatorFactory buildFactory(String rules) throws IOException {
        try (InputStream mapping = new ByteArrayInputStream(rules.getBytes(StandardCharsets.UTF_8))) {
            HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class)
                    .configure()
                    .defaultLocale(Locale.ENGLISH)
                    .addMapping(mapping);
            return configuration.buildValidatorFactory();
        }
    }

    private static Employee validEmployee() {
        Employee employee = new Employee();
        employee.employeeId = "NL204511";
        employee.displayName = "Annabel Lee";
        employee.email = "annabel.lee@example.com";
        employee.taxNumber = "123456782";
        employee.monthlySalary = new BigDecimal("3000.00");
        employee.payFrequency = "MONTHLY";
        employee.payPeriodDays = 30;
        employee.address = new Address("Utrecht", "35112");
        employee.costCentres = List.of("CC-100", "CC-220");
        return employee;
    }

    private static <T> Map<String, String> messagesByPath(Set<ConstraintViolation<T>> violations) {
        Map<String, String> messages = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            messages.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return messages;
    }

    private static <T> Map<Integer, String> messagesByParameterIndex(Set<ConstraintViolation<T>> violations) {
        Map<Integer, String> messages = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            Path.ParameterNode parameter = null;
            for (Path.Node node : violation.getPropertyPath()) {
                if (node instanceof Path.ParameterNode parameterNode) {
                    parameter = parameterNode;
                }
            }
            assertTrue(parameter != null, violation.getPropertyPath().toString());
            messages.put(parameter.getParameterIndex(), violation.getMessage());
        }
        return messages;
    }

    /** Validation group the compliance rules use for checks that only apply when onboarding. */
    public interface Onboarding {
    }

    /** Class-level constraint whose validator the XML mapping supplies. */
    @Documented
    @Constraint(validatedBy = {})
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PayPeriodMatchesFrequency {

        String message() default "pay period does not match the pay frequency";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Checks that the number of days in a pay period fits the employee's pay frequency. */
    public static class PayPeriodMatchesFrequencyValidator
            implements ConstraintValidator<PayPeriodMatchesFrequency, Employee> {

        @Override
        public boolean isValid(Employee employee, ConstraintValidatorContext context) {
            if (employee.payFrequency == null) {
                return true;
            }
            return switch (employee.payFrequency) {
                case "WEEKLY" -> employee.payPeriodDays == 7;
                case "BIWEEKLY" -> employee.payPeriodDays == 14;
                case "MONTHLY" -> employee.payPeriodDays >= 28 && employee.payPeriodDays <= 31;
                default -> false;
            };
        }
    }

    /** An imported employee record; its only annotation is overridden by the XML rules. */
    public static class Employee {

        private String employeeId;

        @Size(max = 3)
        private String displayName;

        private String email;

        private String taxNumber;

        private BigDecimal monthlySalary;

        private String payFrequency;

        private int payPeriodDays;

        private Address address;

        private List<String> costCentres;

        public BigDecimal getAnnualSalary() {
            return monthlySalary.multiply(BigDecimal.valueOf(12));
        }

        public BigDecimal getMonthlySalary() {
            return monthlySalary;
        }
    }

    /** The postal address on an employee record. */
    public static class Address {

        private final String city;

        private final String postalCode;

        public Address(String city, String postalCode) {
            this.city = city;
            this.postalCode = postalCode;
        }
    }

    /** Payroll operation whose parameter rules come from the XML mapping. */
    public static class PayrollService {

        public BigDecimal applyRaise(Employee employee, BigDecimal percent) {
            BigDecimal factor = BigDecimal.ONE.add(percent.movePointLeft(2));
            return employee.getMonthlySalary().multiply(factor);
        }
    }
}
