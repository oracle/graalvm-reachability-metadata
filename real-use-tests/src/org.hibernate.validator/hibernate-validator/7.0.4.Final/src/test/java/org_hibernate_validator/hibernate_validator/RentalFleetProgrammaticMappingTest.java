/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.GenericConstraintDef;
import org.hibernate.validator.cfg.defs.EmailDef;
import org.hibernate.validator.cfg.defs.LengthDef;
import org.hibernate.validator.cfg.defs.MinDef;
import org.hibernate.validator.cfg.defs.NotBlankDef;
import org.hibernate.validator.cfg.defs.NotNullDef;
import org.hibernate.validator.cfg.defs.RangeDef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Programmatic constraint declaration through Hibernate's `ConstraintMapping` API: field, getter
 * and container element constraints declared in code on unannotated classes, cascading enabled
 * by the mapping, a mapping that overrides a field's annotations, group-restricted constraint
 * definitions, and a constraint annotation whose validator is registered as a lambda.
 *
 * A car rental company keeps its fleet model free of validation annotations because the same
 * classes are shared with a pricing engine that must not depend on Jakarta Validation. The
 * rental desk application declares the fleet rules in one mapping when it boots and checks
 * vehicles and drivers against them at the counter and before a car is handed over.
 */
public class RentalFleetProgrammaticMappingTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH);
        configuration.addMapping(fleetRules(configuration.createConstraintMapping()));
        factory = configuration.buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsAVehicleThatMeetsEveryMappedRule() {
        Vehicle vehicle = new Vehicle("Volvo", "B-RX 2041", 5, List.of("child seat", "snow chains"),
                new Driver("Ada Lovelace", "ada@example.com", 36, "Ada"));

        Set<ConstraintViolation<Vehicle>> violations = validator.validate(vehicle);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void reportsFieldAndGetterConstraintsDeclaredInCode() {
        Vehicle vehicle = new Vehicle(null, "X", 12, List.of(), null);

        Set<ConstraintViolation<Vehicle>> violations = validator.validate(vehicle);

        Map<String, String> expected = new TreeMap<>();
        expected.put("manufacturer", "must not be null");
        expected.put("licensePlate", "length must be between 2 and 14");
        expected.put("seatCount", "must be between 2 and 9");
        assertEquals(expected, describe(violations));
    }

    @Test
    void validatesContainerElementsAndCascadesIntoTheDriver() {
        Driver driver = new Driver(" ", "not-an-address", 17, "Ada");
        Vehicle vehicle = new Vehicle("Volvo", "B-RX 2041", 5, List.of("child seat", ""), driver);

        Set<ConstraintViolation<Vehicle>> violations = validator.validate(vehicle);

        Map<String, String> expected = new TreeMap<>();
        expected.put("features[1].<list element>", "must not be blank");
        expected.put("driver.name", "must not be blank");
        expected.put("driver.email", "must be a well-formed email address");
        expected.put("driver.age", "drivers must be at least 18 years old");
        assertEquals(expected, describe(violations));
        ConstraintViolation<Vehicle> ageViolation = violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals("driver.age"))
                .findFirst()
                .orElseThrow();
        assertEquals(17, ageViolation.getInvalidValue());
        assertEquals(driver, ageViolation.getLeafBean());
        assertEquals(vehicle, ageViolation.getRootBean());
    }

    @Test
    void mappingReplacesTheAnnotationsOfAnOverriddenField() {
        Driver longNickname = new Driver("Grace Hopper", "grace@example.com", 45, "Amazing Grace");
        Driver tooLongNickname = new Driver("Grace Hopper", "grace@example.com", 45, "The Queen of Software");

        Set<ConstraintViolation<Driver>> accepted = validator.validate(longNickname);
        Set<ConstraintViolation<Driver>> rejected = validator.validate(tooLongNickname);

        assertTrue(accepted.isEmpty(), () -> "the annotated @Size(max = 4) still applies: " + describe(accepted));
        assertEquals(Map.of("nickname", "length must be between 0 and 16"), describe(rejected));
    }

    @Test
    void checksHandoverRulesOnlyWhenTheHandoverGroupIsRequested() {
        Vehicle unregistered = new Vehicle("Volvo", null, 5, List.of(), null);
        Vehicle foreignPlate = new Vehicle("Volvo", "AB 123 CD", 5, List.of(), null);
        Vehicle localPlate = new Vehicle("Volvo", "B-RX 2041", 5, List.of(), null);

        assertTrue(validator.validate(unregistered).isEmpty());
        assertEquals(Map.of("licensePlate", "must not be null"),
                describe(validator.validate(unregistered, Handover.class)));
        assertEquals(Map.of("licensePlate", "must be a licence plate registered in DE"),
                describe(validator.validate(foreignPlate, Handover.class)));
        assertTrue(validator.validate(localPlate, Handover.class).isEmpty());
    }

    @Test
    void validatesCandidateValuesAgainstMappedConstraints() {
        Set<ConstraintViolation<Vehicle>> tooManySeats = validator.validateValue(Vehicle.class, "seatCount", 10);
        Set<ConstraintViolation<Vehicle>> enoughSeats = validator.validateValue(Vehicle.class, "seatCount", 7);
        Set<ConstraintViolation<Vehicle>> foreignPlate =
                validator.validateValue(Vehicle.class, "licensePlate", "W-12345", Handover.class);

        assertEquals(Map.of("seatCount", "must be between 2 and 9"), describe(tooManySeats));
        assertTrue(enoughSeats.isEmpty());
        assertEquals(Map.of("licensePlate", "must be a licence plate registered in DE"), describe(foreignPlate));
    }

    @Test
    void exposesMappedConstraintsThroughTheMetadataApi() {
        BeanDescriptor vehicle = validator.getConstraintsForClass(Vehicle.class);
        PropertyDescriptor seatCount = vehicle.getConstraintsForProperty("seatCount");
        PropertyDescriptor driver = vehicle.getConstraintsForProperty("driver");
        PropertyDescriptor plate = vehicle.getConstraintsForProperty("licensePlate");

        ConstraintDescriptor<?> seatRange = seatCount.getConstraintDescriptors().iterator().next();
        Set<ConstraintDescriptor<?>> handoverPlateRules = plate.findConstraints()
                .unorderedAndMatchingGroups(Handover.class)
                .getConstraintDescriptors();

        assertTrue(vehicle.isBeanConstrained());
        assertEquals(1, seatCount.getConstraintDescriptors().size());
        assertEquals(2L, seatRange.getAttributes().get("min"));
        assertEquals(9L, seatRange.getAttributes().get("max"));
        assertTrue(driver.isCascaded());
        assertFalse(driver.hasConstraints());
        assertEquals(2, handoverPlateRules.size());
    }

    /** Declares the rental desk's fleet rules on the unannotated fleet model. */
    private static ConstraintMapping fleetRules(ConstraintMapping mapping) {
        mapping.constraintDefinition(RegisteredIn.class)
                .includeExistingValidators(false)
                .validateType(String.class)
                .with(plate -> plate == null || plate.matches("[A-Z]{1,3}-[A-Z]{1,2} [1-9][0-9]{0,3}"));
        mapping.type(Vehicle.class)
                .field("manufacturer")
                    .constraint(new NotNullDef())
                .field("seatCount")
                    .constraint(new RangeDef().min(2).max(9))
                .field("features")
                    .containerElementType()
                        .constraint(new NotBlankDef())
                .field("driver")
                    .valid()
                .getter("licensePlate")
                    .constraint(new LengthDef().min(2).max(14))
                    .constraint(new NotNullDef().groups(Handover.class))
                    .constraint(new GenericConstraintDef<>(RegisteredIn.class)
                            .param("country", "DE")
                            .groups(Handover.class));
        mapping.type(Driver.class)
                .field("name")
                    .constraint(new NotBlankDef())
                .field("email")
                    .constraint(new EmailDef())
                .field("age")
                    .constraint(new MinDef().value(18).message("drivers must be at least 18 years old"))
                .field("nickname")
                    .ignoreAnnotations(true)
                    .constraint(new LengthDef().max(16));
        return mapping;
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        Map<String, String> byPath = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            byPath.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return byPath;
    }

    /** Rules checked when a car is handed over to a customer. */
    public interface Handover {
    }

    /** A licence plate issued in the given country; its validator is registered in the mapping. */
    @Documented
    @Constraint(validatedBy = {})
    @Target({ElementType.METHOD, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RegisteredIn {

        String message() default "must be a licence plate registered in {country}";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        String country();
    }

    /** A fleet vehicle, shared with the pricing engine and therefore free of annotations. */
    public static class Vehicle {

        final String manufacturer;
        final String licensePlate;
        final int seatCount;
        final List<String> features;
        final Driver driver;

        Vehicle(String manufacturer, String licensePlate, int seatCount, List<String> features, Driver driver) {
            this.manufacturer = manufacturer;
            this.licensePlate = licensePlate;
            this.seatCount = seatCount;
            this.features = features;
            this.driver = driver;
        }

        public String getLicensePlate() {
            return licensePlate;
        }
    }

    /** The driver a vehicle is rented to. */
    public static class Driver {

        final String name;
        final String email;
        final int age;

        /** The pricing engine's short label; the rental desk allows a longer one. */
        @Size(max = 4)
        final String nickname;

        Driver(String name, String email, int age, String nickname) {
            this.name = name;
            this.email = email;
            this.age = age;
            this.nickname = nickname;
        }
    }
}
