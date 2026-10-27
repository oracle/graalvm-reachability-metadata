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
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.HibernateValidatorFactory;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

/**
 * Validator runtime configuration: an application ConstraintValidatorFactory injecting a service
 * into validators and releasing them when the factory closes, constraint validator payloads set on
 * the factory and per validator, dynamic payloads and message parameters on violations, fail-fast
 * mode from the API, the configuration property and the validator context, and the EL-free
 * ParameterMessageInterpolator.
 *
 * A freight forwarder screens booking requests: the destination port is checked against an
 * embargo registry for the jurisdiction the validating office operates under.
 */
public class FreightBookingValidatorRuntimeConfigurationTest {

    private static final String SHIPPER = "Nordic Timber AB";
    private static final String CONTAINER = "MSKU1234567";

    @Test
    void injectedValidatorScreensDestinationPortForFactoryJurisdiction() {
        EmbargoRegistry registry = new EmbargoRegistry();
        RegistryAwareValidatorFactory validatorFactory = new RegistryAwareValidatorFactory(registry);
        try (ValidatorFactory factory = configure(validatorFactory)
                .constraintValidatorPayload(Jurisdiction.EU)
                .buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<FreightBooking>> violations =
                    validator.validate(new FreightBooking(SHIPPER, CONTAINER, 18_400, "RUVVO", "Baltic Star"));

            assertEquals(1, violations.size());
            ConstraintViolation<FreightBooking> violation = violations.iterator().next();
            assertEquals("destinationPort", violation.getPropertyPath().toString());
            assertEquals("RUVVO is under embargo in EU (EU-833/2014)", violation.getMessage());
            assertEquals("{port} is under embargo in {jurisdiction} ({reference})", violation.getMessageTemplate());

            ScreeningHint hint = screeningHint(violation);
            assertEquals("RUVVO", hint.port);
            assertEquals("EU-833/2014", hint.reference);
            assertEquals("KRPUS", hint.suggestedPort);

            assertTrue(validator.validate(new FreightBooking(SHIPPER, CONTAINER, 18_400, "IRBND", "Baltic Star"))
                    .isEmpty());
            assertEquals(2, registry.lookups);
            assertEquals(1, validatorFactory.portValidators().size());
            assertSame(registry, validatorFactory.portValidators().get(0).registry);
        }
    }

    @Test
    void validatorContextPayloadSwitchesJurisdictionPerOffice() {
        EmbargoRegistry registry = new EmbargoRegistry();
        RegistryAwareValidatorFactory validatorFactory = new RegistryAwareValidatorFactory(registry);
        try (ValidatorFactory factory = configure(validatorFactory)
                .constraintValidatorPayload(Jurisdiction.EU)
                .buildValidatorFactory()) {
            FreightBooking toBandarAbbas = new FreightBooking(SHIPPER, CONTAINER, 9_200, "IRBND", "Gulf Trader");

            Validator euOffice = factory.getValidator();
            Validator usOffice = factory.unwrap(HibernateValidatorFactory.class)
                    .usingContext()
                    .constraintValidatorPayload(Jurisdiction.US)
                    .getValidator();

            assertTrue(euOffice.validate(toBandarAbbas).isEmpty());
            Set<ConstraintViolation<FreightBooking>> usViolations = usOffice.validate(toBandarAbbas);
            assertEquals(1, usViolations.size());
            ConstraintViolation<FreightBooking> violation = usViolations.iterator().next();
            assertEquals("IRBND is under embargo in US (OFAC-ITSR)", violation.getMessage());
            assertEquals("AEJEA", screeningHint(violation).suggestedPort);

            Set<ConstraintViolation<FreightBooking>> usPortCheck =
                    usOffice.validateValue(FreightBooking.class, "destinationPort", "RUVVO");
            assertEquals("RUVVO is under embargo in US (OFAC-RuHSR)", usPortCheck.iterator().next().getMessage());

            assertEquals(1, validatorFactory.portValidators().size());
        }

        try (ValidatorFactory unscreened = configure(new RegistryAwareValidatorFactory(registry))
                .buildValidatorFactory()) {
            assertTrue(unscreened.getValidator()
                    .validate(new FreightBooking(SHIPPER, CONTAINER, 9_200, "RUVVO", "Gulf Trader"))
                    .isEmpty());
        }
    }

    @Test
    void failFastReportsOnlyTheFirstViolation() {
        EmbargoRegistry registry = new EmbargoRegistry();
        FreightBooking sloppy = new FreightBooking(" ", "MSK-12", -40, "RUVVO", "Baltic Star");
        Set<String> failingProperties = Set.of("shipper", "containerNumber", "grossWeightKg", "destinationPort");

        try (ValidatorFactory factory = configure(new RegistryAwareValidatorFactory(registry))
                .constraintValidatorPayload(Jurisdiction.EU)
                .buildValidatorFactory()) {
            Set<ConstraintViolation<FreightBooking>> all = factory.getValidator().validate(sloppy);
            assertEquals(failingProperties, propertyPaths(all));

            Validator failFastOffice = factory.unwrap(HibernateValidatorFactory.class)
                    .usingContext()
                    .failFast(true)
                    .getValidator();
            assertSingleViolationAmong(failingProperties, failFastOffice.validate(sloppy));
        }

        try (ValidatorFactory factory = configure(new RegistryAwareValidatorFactory(registry))
                .constraintValidatorPayload(Jurisdiction.EU)
                .failFast(true)
                .buildValidatorFactory()) {
            assertSingleViolationAmong(failingProperties, factory.getValidator().validate(sloppy));
        }

        try (ValidatorFactory factory = configure(new RegistryAwareValidatorFactory(registry))
                .constraintValidatorPayload(Jurisdiction.EU)
                .addProperty(HibernateValidatorConfiguration.FAIL_FAST, "true")
                .buildValidatorFactory()) {
            assertSingleViolationAmong(failingProperties, factory.getValidator().validate(sloppy));
        }
    }

    @Test
    void parameterMessageInterpolatorKeepsExpressionsVerbatim() {
        EmbargoRegistry registry = new EmbargoRegistry();
        try (ValidatorFactory withoutEl = configure(new RegistryAwareValidatorFactory(registry))
                .constraintValidatorPayload(Jurisdiction.EU)
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
                ValidatorFactory withEl = configure(new RegistryAwareValidatorFactory(registry))
                        .constraintValidatorPayload(Jurisdiction.EU)
                        .buildValidatorFactory()) {
            Validator parameterOnly = withoutEl.getValidator();

            assertEquals("vessel name must be 2 to 35 characters, got '${validatedValue}'",
                    parameterOnly.validateValue(FreightBooking.class, "vesselName", "X")
                            .iterator().next().getMessage());
            assertEquals("vessel name must be 2 to 35 characters, got 'X'",
                    withEl.getValidator().validateValue(FreightBooking.class, "vesselName", "X")
                            .iterator().next().getMessage());

            assertEquals("RUVVO is under embargo in EU (EU-833/2014)",
                    parameterOnly.validateValue(FreightBooking.class, "destinationPort", "RUVVO")
                            .iterator().next().getMessage());
        }
    }

    @Test
    void closingTheFactoryReleasesEveryValidatorItCreated() {
        EmbargoRegistry registry = new EmbargoRegistry();
        RegistryAwareValidatorFactory validatorFactory = new RegistryAwareValidatorFactory(registry);
        ValidatorFactory factory = configure(validatorFactory)
                .constraintValidatorPayload(Jurisdiction.US)
                .buildValidatorFactory();

        factory.getValidator().validate(new FreightBooking(SHIPPER, CONTAINER, 12_000, "NLRTM", "Rhine Pilot"));
        assertEquals(1, validatorFactory.portValidators().size());
        assertTrue(validatorFactory.released.isEmpty());

        factory.close();

        assertEquals(validatorFactory.created.size(), validatorFactory.released.size());
        assertTrue(validatorFactory.released.stream()
                .anyMatch(released -> released == validatorFactory.portValidators().get(0)));
    }

    private static HibernateValidatorConfiguration configure(RegistryAwareValidatorFactory validatorFactory) {
        HibernateValidatorConfiguration configuration = Validation.byProvider(HibernateValidator.class).configure();
        validatorFactory.delegate = configuration.getDefaultConstraintValidatorFactory();
        return configuration.constraintValidatorFactory(validatorFactory);
    }

    private static ScreeningHint screeningHint(ConstraintViolation<FreightBooking> violation) {
        HibernateConstraintViolation<?> hibernateViolation = violation.unwrap(HibernateConstraintViolation.class);
        return hibernateViolation.getDynamicPayload(ScreeningHint.class);
    }

    private static void assertSingleViolationAmong(Set<String> failingProperties,
            Set<ConstraintViolation<FreightBooking>> violations) {
        assertEquals(1, violations.size());
        assertTrue(failingProperties.containsAll(propertyPaths(violations)));
    }

    private static Set<String> propertyPaths(Set<ConstraintViolation<FreightBooking>> violations) {
        Set<String> paths = new TreeSet<>();
        for (ConstraintViolation<FreightBooking> violation : violations) {
            paths.add(violation.getPropertyPath().toString());
        }
        return paths;
    }

    enum Jurisdiction {
        EU,
        US
    }

    /** Embargo lists per jurisdiction, as the forwarder's compliance service publishes them. */
    static final class EmbargoRegistry {

        private final Map<Jurisdiction, Map<String, String>> embargoes = Map.of(
                Jurisdiction.EU, Map.of("RUVVO", "EU-833/2014"),
                Jurisdiction.US, Map.of("RUVVO", "OFAC-RuHSR", "IRBND", "OFAC-ITSR"));

        private final Map<String, String> alternatives = Map.of("RUVVO", "KRPUS", "IRBND", "AEJEA");

        int lookups;

        Optional<String> sanctionReference(Jurisdiction jurisdiction, String port) {
            lookups++;
            return Optional.ofNullable(embargoes.get(jurisdiction).get(port));
        }

        String alternativeFor(String port) {
            return alternatives.get(port);
        }
    }

    /** Remediation attached to an embargo violation so the booking UI can propose another port. */
    static final class ScreeningHint {

        final String port;
        final String reference;
        final String suggestedPort;

        ScreeningHint(String port, String reference, String suggestedPort) {
            this.port = port;
            this.reference = reference;
            this.suggestedPort = suggestedPort;
        }
    }

    /** Builds validators the way a DI container would, handing the registry to the ones that need it. */
    static final class RegistryAwareValidatorFactory implements ConstraintValidatorFactory {

        private final EmbargoRegistry registry;
        private final List<ConstraintValidator<?, ?>> created = new ArrayList<>();
        private final List<ConstraintValidator<?, ?>> released = new ArrayList<>();
        private ConstraintValidatorFactory delegate;

        RegistryAwareValidatorFactory(EmbargoRegistry registry) {
            this.registry = registry;
        }

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            T instance = key == PortOpenForTradeValidator.class
                    ? key.cast(new PortOpenForTradeValidator(registry))
                    : delegate.getInstance(key);
            created.add(instance);
            return instance;
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
            released.add(instance);
            if (!(instance instanceof PortOpenForTradeValidator)) {
                delegate.releaseInstance(instance);
            }
        }

        List<PortOpenForTradeValidator> portValidators() {
            List<PortOpenForTradeValidator> portValidators = new ArrayList<>();
            for (ConstraintValidator<?, ?> validator : created) {
                if (validator instanceof PortOpenForTradeValidator) {
                    portValidators.add((PortOpenForTradeValidator) validator);
                }
            }
            return portValidators;
        }
    }

    @Documented
    @Constraint(validatedBy = PortOpenForTradeValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PortOpenForTrade {

        String message() default "{port} is under embargo in {jurisdiction} ({reference})";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Has no no-arg constructor: only the application factory can build it. */
    public static final class PortOpenForTradeValidator implements ConstraintValidator<PortOpenForTrade, String> {

        final EmbargoRegistry registry;

        PortOpenForTradeValidator(EmbargoRegistry registry) {
            this.registry = registry;
        }

        @Override
        public boolean isValid(String port, ConstraintValidatorContext context) {
            if (port == null) {
                return true;
            }
            HibernateConstraintValidatorContext hibernateContext =
                    context.unwrap(HibernateConstraintValidatorContext.class);
            Jurisdiction jurisdiction = hibernateContext.getConstraintValidatorPayload(Jurisdiction.class);
            if (jurisdiction == null) {
                return true;
            }
            Optional<String> reference = registry.sanctionReference(jurisdiction, port);
            if (reference.isEmpty()) {
                return true;
            }
            hibernateContext.addMessageParameter("port", port)
                    .addMessageParameter("jurisdiction", jurisdiction)
                    .addMessageParameter("reference", reference.get())
                    .withDynamicPayload(new ScreeningHint(port, reference.get(), registry.alternativeFor(port)));
            return false;
        }
    }

    public static final class FreightBooking {

        @NotBlank
        final String shipper;

        @Pattern(regexp = "[A-Z]{3}U\\d{7}")
        final String containerNumber;

        @Positive
        final int grossWeightKg;

        @PortOpenForTrade
        final String destinationPort;

        @Size(min = 2, max = 35, message = "vessel name must be {min} to {max} characters, got '${validatedValue}'")
        final String vesselName;

        FreightBooking(String shipper, String containerNumber, int grossWeightKg, String destinationPort,
                String vesselName) {
            this.shipper = shipper;
            this.containerNumber = containerNumber;
            this.grossWeightKg = grossWeightKg;
            this.destinationPort = destinationPort;
            this.vesselName = vesselName;
        }
    }
}
