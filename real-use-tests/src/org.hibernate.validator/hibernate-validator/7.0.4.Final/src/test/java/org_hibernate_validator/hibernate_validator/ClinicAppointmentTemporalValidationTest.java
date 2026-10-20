/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorFactory;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Temporal constraint validation against an application clock: `@Past`, `@PastOrPresent`,
 * `@Future` and `@FutureOrPresent` on java.time, legacy `Date`/`Calendar` and Joda-Time values,
 * evaluated through a `ClockProvider`, with temporal validation tolerance, per-validator clocks,
 * `@DurationMin`/`@DurationMax` on appointment lengths and a custom constraint reading the clock.
 *
 * A clinic booking service validates appointment requests. Its clock is injected so that the
 * service can be tested and audited at a chosen instant: the scenario fixes "now" to a known
 * instant, moves it forward between validations, tolerates small clock skew from booking
 * kiosks, and replays an old booking against the clock of the day it was made.
 */
public class ClinicAppointmentTemporalValidationTest {

    private static final Instant NOW = Instant.parse("2026-03-10T09:00:00Z");

    private ClinicClock clock;
    private HibernateValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        clock = new ClinicClock(NOW);
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .clockProvider(() -> clock)
                .buildValidatorFactory()
                .unwrap(HibernateValidatorFactory.class);
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsAppointmentConsistentWithTheClinicClock() {
        Appointment appointment = validAppointment();

        Set<ConstraintViolation<Appointment>> violations = validator.validate(appointment);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void reportsEveryTemporalViolation() {
        Appointment appointment = validAppointment();
        appointment.start = NOW.minus(Duration.ofHours(1));
        appointment.length = Duration.ofMinutes(10);
        appointment.patientBirthDate = LocalDate.of(2026, 3, 11);
        appointment.bookedAt = OffsetDateTime.parse("2026-03-10T10:30:00+01:00").plusMinutes(1);
        appointment.referralValidUntil = LocalDate.of(2026, 3, 9);
        appointment.insuranceCardIssued = Date.from(NOW.plus(Duration.ofDays(1)));
        appointment.lastPaymentDue = calendarAt(NOW.plus(Duration.ofDays(3)));
        appointment.reminderAt = new DateTime(2026, 3, 10, 8, 0, DateTimeZone.UTC);
        appointment.lastVisit = new LocalDateTime(2026, 4, 1, 10, 0);
        appointment.billingPeriod = YearMonth.of(2026, 2);
        appointment.registrationYear = Year.of(2027);

        Set<ConstraintViolation<Appointment>> violations = validator.validate(appointment);

        Map<String, String> expected = new TreeMap<>();
        expected.put("start", "must be a future date");
        expected.put("length", "must be longer than or equal to 15 minutes");
        expected.put("patientBirthDate", "must be a past date");
        expected.put("bookedAt", "must be a date in the past or in the present");
        expected.put("referralValidUntil", "must be a date in the present or in the future");
        expected.put("insuranceCardIssued", "must be a past date");
        expected.put("lastPaymentDue", "must be a past date");
        expected.put("reminderAt", "must be a future date");
        expected.put("lastVisit", "must be a past date");
        expected.put("billingPeriod", "must be a date in the present or in the future");
        expected.put("registrationYear", "must be a date in the past or in the present");
        assertEquals(expected, describe(violations));
    }

    @Test
    void reevaluatesConstraintsWhenTheClinicClockAdvances() {
        Appointment appointment = validAppointment();
        assertTrue(validator.validate(appointment).isEmpty());

        clock.advance(Duration.ofDays(2));
        Set<ConstraintViolation<Appointment>> afterTwoDays = validator.validate(appointment);

        Map<String, String> expected = new TreeMap<>();
        expected.put("start", "must be a future date");
        expected.put("reminderAt", "must be a future date");
        expected.put("referralValidUntil", "must be a date in the present or in the future");
        assertEquals(expected, describe(afterTwoDays));
    }

    @Test
    void presentBoundaryIsAcceptedOnlyByTheOrPresentVariants() {
        Set<ConstraintViolation<Appointment>> startNow = validator.validateValue(Appointment.class, "start", NOW);
        Set<ConstraintViolation<Appointment>> bookedNow = validator.validateValue(
                Appointment.class, "bookedAt", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        Set<ConstraintViolation<Appointment>> referralToday = validator.validateValue(
                Appointment.class, "referralValidUntil", LocalDate.of(2026, 3, 10));
        Set<ConstraintViolation<Appointment>> billingThisMonth = validator.validateValue(
                Appointment.class, "billingPeriod", YearMonth.of(2026, 3));

        assertEquals(Map.of("start", "must be a future date"), describe(startNow));
        assertTrue(bookedNow.isEmpty(), () -> "unexpected violations: " + describe(bookedNow));
        assertTrue(referralToday.isEmpty(), () -> "unexpected violations: " + describe(referralToday));
        assertTrue(billingThisMonth.isEmpty(), () -> "unexpected violations: " + describe(billingThisMonth));
    }

    @Test
    void toleratesKioskClockSkewWithinTheConfiguredTolerance() {
        Validator kioskValidator = factory.usingContext()
                .temporalValidationTolerance(Duration.ofSeconds(30))
                .getValidator();
        Instant slightlyLateStart = NOW.minus(Duration.ofSeconds(10));
        Instant clearlyLateStart = NOW.minus(Duration.ofMinutes(1));
        Date slightlyFutureIssue = Date.from(NOW.plus(Duration.ofSeconds(20)));

        assertEquals(Map.of("start", "must be a future date"),
                describe(validator.validateValue(Appointment.class, "start", slightlyLateStart)));
        assertTrue(kioskValidator.validateValue(Appointment.class, "start", slightlyLateStart).isEmpty());
        assertTrue(kioskValidator.validateValue(Appointment.class, "insuranceCardIssued", slightlyFutureIssue)
                .isEmpty());
        assertEquals(Map.of("start", "must be a future date"),
                describe(kioskValidator.validateValue(Appointment.class, "start", clearlyLateStart)));
    }

    @Test
    void auditsAnOldBookingAgainstTheClockOfTheDayItWasMade() {
        Instant bookingDay = Instant.parse("2025-11-03T14:00:00Z");
        ClinicClock auditClock = new ClinicClock(bookingDay);
        Validator auditValidator = factory.usingContext()
                .clockProvider(() -> auditClock)
                .getValidator();
        Appointment historical = validAppointment();
        historical.start = Instant.parse("2025-11-05T10:00:00Z");
        historical.bookedAt = OffsetDateTime.parse("2025-11-03T13:55:00Z");
        historical.referralValidUntil = LocalDate.of(2025, 12, 31);
        historical.reminderAt = new DateTime(2025, 11, 4, 18, 0, DateTimeZone.UTC);
        historical.lastVisit = new LocalDateTime(2025, 6, 2, 10, 0);
        historical.billingPeriod = YearMonth.of(2025, 11);
        historical.registrationYear = Year.of(2025);
        historical.insuranceCardIssued = Date.from(Instant.parse("2024-01-15T00:00:00Z"));
        historical.lastPaymentDue = calendarAt(Instant.parse("2025-10-31T00:00:00Z"));
        historical.horizonEnd = LocalDate.of(2025, 11, 20);

        Set<ConstraintViolation<Appointment>> asOfBookingDay = auditValidator.validate(historical);
        Set<ConstraintViolation<Appointment>> asOfToday = validator.validate(historical);

        assertTrue(asOfBookingDay.isEmpty(), () -> "unexpected violations: " + describe(asOfBookingDay));
        Map<String, String> expected = new TreeMap<>();
        expected.put("start", "must be a future date");
        expected.put("reminderAt", "must be a future date");
        expected.put("referralValidUntil", "must be a date in the present or in the future");
        expected.put("billingPeriod", "must be a date in the present or in the future");
        assertEquals(expected, describe(asOfToday));
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void enforcesInclusiveAndExclusiveDurationBounds() {
        assertTrue(validator.validateValue(Appointment.class, "length", Duration.ofMinutes(15)).isEmpty());
        assertTrue(validator.validateValue(Appointment.class, "length", Duration.ofMinutes(90)).isEmpty());
        assertEquals(Map.of("length", "must be longer than or equal to 15 minutes"),
                describe(validator.validateValue(Appointment.class, "length", Duration.ofSeconds(899))));
        assertEquals(Map.of("length", "must be shorter than 2 hours"),
                describe(validator.validateValue(Appointment.class, "length", Duration.ofHours(2))));

        Set<ConstraintViolation<Appointment>> tooLong =
                validator.validateValue(Appointment.class, "length", Duration.ofHours(3));
        ConstraintDescriptor<?> descriptor = tooLong.iterator().next().getConstraintDescriptor();
        assertEquals(DurationMax.class, descriptor.getAnnotation().annotationType());
        assertEquals(2L, descriptor.getAttributes().get("hours"));
        assertEquals(false, descriptor.getAttributes().get("inclusive"));
    }

    @Test
    void customConstraintReadsTheBookingHorizonFromTheClinicClock() {
        Appointment appointment = validAppointment();
        appointment.horizonEnd = LocalDate.of(2026, 4, 10);

        Set<ConstraintViolation<Appointment>> tooFarAhead = validator.validate(appointment);
        assertEquals(Map.of("horizonEnd", "must be at most 30 days after the booking date"),
                describe(tooFarAhead));

        clock.advance(Duration.ofDays(5));
        appointment.start = NOW.plus(Duration.ofDays(6));
        appointment.reminderAt = new DateTime(2026, 3, 16, 7, 0, DateTimeZone.UTC);
        appointment.referralValidUntil = LocalDate.of(2026, 3, 31);
        Set<ConstraintViolation<Appointment>> withinHorizon = validator.validate(appointment);
        assertTrue(withinHorizon.isEmpty(), () -> "unexpected violations: " + describe(withinHorizon));
    }

    private static Appointment validAppointment() {
        Appointment appointment = new Appointment();
        appointment.start = Instant.parse("2026-03-11T08:30:00Z");
        appointment.length = Duration.ofMinutes(45);
        appointment.patientBirthDate = LocalDate.of(1984, 7, 21);
        appointment.bookedAt = OffsetDateTime.parse("2026-03-10T09:45:00+01:00");
        appointment.referralValidUntil = LocalDate.of(2026, 3, 11);
        appointment.insuranceCardIssued = Date.from(Instant.parse("2024-01-15T00:00:00Z"));
        appointment.lastPaymentDue = calendarAt(Instant.parse("2026-02-28T00:00:00Z"));
        appointment.reminderAt = new DateTime(2026, 3, 10, 18, 0, DateTimeZone.UTC);
        appointment.lastVisit = new LocalDateTime(2025, 9, 14, 10, 0);
        appointment.billingPeriod = YearMonth.of(2026, 3);
        appointment.registrationYear = Year.of(2019);
        appointment.horizonEnd = LocalDate.of(2026, 4, 1);
        return appointment;
    }

    private static Calendar calendarAt(Instant instant) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
        calendar.setTimeInMillis(instant.toEpochMilli());
        return calendar;
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        Map<String, String> described = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            described.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return described;
    }

    /// The clinic's injectable clock; tests move it forward instead of waiting for time to pass.
    static final class ClinicClock extends Clock {

        private Instant current;

        ClinicClock(Instant start) {
            this.current = start;
        }

        void advance(Duration amount) {
            current = current.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    /// The last day a self-service booking may still be scheduled, relative to the clinic clock.
    @Documented
    @Constraint(validatedBy = WithinBookingHorizonValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface WithinBookingHorizon {

        String message() default "must be at most {days} days after the booking date";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        int days();
    }

    public static final class WithinBookingHorizonValidator
            implements ConstraintValidator<WithinBookingHorizon, LocalDate> {

        private int days;

        @Override
        public void initialize(WithinBookingHorizon constraint) {
            days = constraint.days();
        }

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            LocalDate today = LocalDate.now(context.getClockProvider().getClock());
            return !value.isAfter(today.plusDays(days));
        }
    }

    static final class Appointment {

        @NotNull
        @Future
        Instant start;

        @DurationMin(minutes = 15)
        @DurationMax(hours = 2, inclusive = false)
        Duration length;

        @Past
        LocalDate patientBirthDate;

        @PastOrPresent
        OffsetDateTime bookedAt;

        @FutureOrPresent
        LocalDate referralValidUntil;

        @Past
        Date insuranceCardIssued;

        @Past
        Calendar lastPaymentDue;

        @Future
        DateTime reminderAt;

        @Past
        LocalDateTime lastVisit;

        @FutureOrPresent
        YearMonth billingPeriod;

        @PastOrPresent
        Year registrationYear;

        @WithinBookingHorizon(days = 30)
        LocalDate horizonEnd;
    }
}
