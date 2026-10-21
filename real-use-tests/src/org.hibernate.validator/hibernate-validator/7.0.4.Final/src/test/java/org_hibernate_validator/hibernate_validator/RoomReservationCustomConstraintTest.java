/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Custom constraint definitions: application-defined `@Constraint` annotations backed by
 * `ConstraintValidator` implementations, a composed constraint reported as a single violation,
 * a class-level cross-field constraint, and Expression Language message interpolation with
 * annotation attributes, the validated value and validator-supplied expression variables,
 * which a validator must opt into for the messages it builds itself.
 *
 * A hotel booking service validates reservations before it holds a room. Its domain rules do
 * not fit the built-in constraints, so the service defines its own: a room code format, a
 * guest limit per room and a stay that must end after it starts and last at most two weeks.
 * The scenario asserts the messages a guest would see on the booking form.
 */
public class RoomReservationCustomConstraintTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsValidReservation() {
        Reservation reservation = new Reservation("B-204", 2, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4));

        Set<ConstraintViolation<Reservation>> violations = validator.validate(reservation);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void interpolatesAttributesAndValidatedValueThroughExpressionLanguage() {
        Reservation reservation = new Reservation("b204", 5, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4));

        Set<ConstraintViolation<Reservation>> violations = validator.validate(reservation);

        Map<String, String> expected = new TreeMap<>();
        expected.put("roomCode", "room code 'b204' must look like A-123");
        expected.put("guests", "at most 4 guests fit in a room, but 5 were requested");
        assertEquals(expected, describe(violations));
    }

    @Test
    void choosesSingularWordingWithConditionalExpression() {
        Set<ConstraintViolation<SingleRoom>> violations = validator.validate(new SingleRoom(3));

        assertEquals(Map.of("guests", "at most 1 guest fit in a room, but 3 were requested"), describe(violations));
    }

    @Test
    void reportsComposedConstraintAsSingleViolation() {
        Reservation reservation = new Reservation(null, 2, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4));

        Set<ConstraintViolation<Reservation>> violations = validator.validate(reservation);

        assertEquals(1, violations.size());
        ConstraintViolation<Reservation> violation = violations.iterator().next();
        assertEquals("roomCode", violation.getPropertyPath().toString());
        assertEquals("room code '' must look like A-123", violation.getMessage());
        assertEquals(RoomCode.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
    }

    @Test
    void reportsClassLevelRuleOnTheCheckOutProperty() {
        Reservation backwards = new Reservation("B-204", 2, LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 1));
        Reservation tooLong = new Reservation("B-204", 2, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 21));

        Set<ConstraintViolation<Reservation>> backwardsViolations = validator.validate(backwards);
        Set<ConstraintViolation<Reservation>> tooLongViolations = validator.validate(tooLong);

        assertEquals(Map.of("checkOut", "check-out must be after check-in"), describe(backwardsViolations));
        assertEquals(Map.of("checkOut", "a stay of 20 nights exceeds the limit of 14 nights"), describe(tooLongViolations));
        ConstraintViolation<Reservation> violation = tooLongViolations.iterator().next();
        assertSame(tooLong, violation.getLeafBean());
        assertEquals(StayPeriod.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void describesCustomConstraintsThroughMetadataApi() {
        BeanDescriptor descriptor = validator.getConstraintsForClass(Reservation.class);

        Set<ConstraintDescriptor<?>> classConstraints = descriptor.getConstraintDescriptors();
        ConstraintDescriptor<?> stayPeriod = classConstraints.iterator().next();
        ConstraintDescriptor<?> roomCode = descriptor.getConstraintsForProperty("roomCode")
                .getConstraintDescriptors()
                .iterator()
                .next();

        assertEquals(1, classConstraints.size());
        assertEquals(14, stayPeriod.getAttributes().get("maxNights"));
        assertTrue(roomCode.isReportAsSingleViolation());
        assertEquals(
                Set.of(NotNull.class, Pattern.class),
                roomCode.getComposingConstraints().stream()
                        .map(composing -> composing.getAnnotation().annotationType())
                        .collect(Collectors.toSet()));
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        Map<String, String> byPath = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            byPath.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return byPath;
    }

    /** A room code such as `B-204`, composed from built-in constraints. */
    @NotNull
    @Pattern(regexp = "[A-Z]-[0-9]{3}")
    @ReportAsSingleViolation
    @Constraint(validatedBy = {})
    @Documented
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RoomCode {

        String message() default "room code '${validatedValue}' must look like A-123";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** The number of guests a room holds. */
    @Constraint(validatedBy = MaxGuestsValidator.class)
    @Documented
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MaxGuests {

        String message() default "at most {value} guest${value == 1 ? '' : 's'} fit in a room,"
                + " but ${validatedValue} were requested";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        int value();
    }

    /** A stay that ends after it starts and lasts at most `maxNights` nights. */
    @Constraint(validatedBy = StayPeriodValidator.class)
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StayPeriod {

        String message() default "check-out must be after check-in";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        int maxNights();
    }

    public static class MaxGuestsValidator implements ConstraintValidator<MaxGuests, Integer> {

        private int limit;

        @Override
        public void initialize(MaxGuests annotation) {
            limit = annotation.value();
        }

        @Override
        public boolean isValid(Integer guests, ConstraintValidatorContext context) {
            return guests == null || guests <= limit;
        }
    }

    public static class StayPeriodValidator implements ConstraintValidator<StayPeriod, Reservation> {

        private int maxNights;

        @Override
        public void initialize(StayPeriod annotation) {
            maxNights = annotation.maxNights();
        }

        @Override
        public boolean isValid(Reservation reservation, ConstraintValidatorContext context) {
            if (reservation.checkIn == null || reservation.checkOut == null) {
                return true;
            }
            long nights = ChronoUnit.DAYS.between(reservation.checkIn, reservation.checkOut);
            context.disableDefaultConstraintViolation();
            if (nights <= 0) {
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("checkOut")
                        .addConstraintViolation();
                return false;
            }
            if (nights > maxNights) {
                context.unwrap(HibernateConstraintValidatorContext.class)
                        .addExpressionVariable("nights", nights)
                        .buildConstraintViolationWithTemplate(
                                "a stay of ${nights} nights exceeds the limit of {maxNights} nights")
                        .enableExpressionLanguage()
                        .addPropertyNode("checkOut")
                        .addConstraintViolation();
                return false;
            }
            return true;
        }
    }

    @StayPeriod(maxNights = 14)
    public static class Reservation {

        @RoomCode
        final String roomCode;

        @MaxGuests(4)
        final int guests;

        final LocalDate checkIn;

        final LocalDate checkOut;

        Reservation(String roomCode, int guests, LocalDate checkIn, LocalDate checkOut) {
            this.roomCode = roomCode;
            this.guests = guests;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
        }
    }

    public static class SingleRoom {

        @MaxGuests(1)
        final int guests;

        SingleRoom(int guests) {
            this.guests = guests;
        }
    }
}
