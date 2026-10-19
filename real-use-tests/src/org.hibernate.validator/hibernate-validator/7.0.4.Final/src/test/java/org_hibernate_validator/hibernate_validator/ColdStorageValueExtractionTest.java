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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ContainerElementTypeDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import jakarta.validation.metadata.ValidateUnwrappedValue;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.Unwrapping;
import jakarta.validation.valueextraction.ValueExtractor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Value extraction for container types: application `ValueExtractor` implementations for a
 * generic and a non-generic container registered on the factory, `@UnwrapByDefault` and the
 * `Unwrapping` payloads, the built-in `Optional` and `OptionalInt` extractors, custom
 * containers nested in a `Map`, a validator-level extractor override, container element
 * metadata, and the error raised when a constrained container has no extractor.
 *
 * A cold-chain warehouse keeps sensor readings in its own `Tracked` wrapper, which holds the
 * current value and the earlier readings, and pallet labels in a `Barcode` value type. The
 * warehouse application constrains the values inside those wrappers rather than the wrappers
 * themselves, and its compliance audit re-validates every historical reading.
 */
public class ColdStorageValueExtractionTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void createValidator() {
        factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .addValueExtractor(new CurrentReadingExtractor())
                .addValueExtractor(new BarcodeDigitsExtractor())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        factory.close();
    }

    @Test
    void acceptsAUnitWhoseWrappedValuesAreAllValid() {
        StorageUnit unit = compliantUnit();

        Set<ConstraintViolation<StorageUnit>> violations = validator.validate(unit);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void validatesTheCurrentReadingExtractedFromTheCustomContainer() {
        StorageUnit unit = compliantUnit();
        unit.temperature = new Tracked<>(new BigDecimal("9.5"), List.of(new BigDecimal("4.0")));

        Set<ConstraintViolation<StorageUnit>> violations = validator.validate(unit);

        assertEquals(Set.of("temperature.<current reading>: must be less than or equal to 8.0"),
                describe(violations));
        ConstraintViolation<StorageUnit> violation = violations.iterator().next();
        assertEquals(new BigDecimal("9.5"), violation.getInvalidValue());
        assertEquals(unit, violation.getLeafBean());
    }

    @Test
    void unwrapsTheNonGenericBarcodeByDefaultUnlessThePayloadSkipsIt() {
        StorageUnit shortLabel = compliantUnit();
        shortLabel.barcode = new Barcode("12AB");
        StorageUnit missingLabel = compliantUnit();
        missingLabel.barcode = null;

        Set<ConstraintViolation<StorageUnit>> shortViolations = validator.validate(shortLabel);
        Set<ConstraintViolation<StorageUnit>> missingViolations = validator.validate(missingLabel);

        assertEquals(Set.of("barcode: size must be between 8 and 13",
                "barcode: must match \"\\d+\""), describe(shortViolations));
        assertTrue(shortViolations.stream().allMatch(violation -> "12AB".equals(violation.getInvalidValue())),
                "the unwrapped digits, not the Barcode, are the invalid value");
        assertEquals(Set.of("barcode: must not be null"), describe(missingViolations));
    }

    @Test
    void validatesValuesInsideTheBuiltInOptionalContainers() {
        StorageUnit unit = compliantUnit();
        unit.reorderThreshold = Optional.of(0);
        unit.shelfLifeDays = OptionalInt.of(400);
        StorageUnit emptyOptionals = compliantUnit();
        emptyOptionals.reorderThreshold = Optional.empty();
        emptyOptionals.shelfLifeDays = OptionalInt.empty();

        Set<ConstraintViolation<StorageUnit>> violations = validator.validate(unit);

        assertEquals(Set.of("reorderThreshold: must be greater than 0",
                "shelfLifeDays: must be less than or equal to 365"), describe(violations));
        assertTrue(validator.validate(emptyOptionals).isEmpty());
    }

    @Test
    void reachesCustomContainersNestedInAMap() {
        StorageUnit unit = compliantUnit();
        unit.humidityByZone = Map.of(
                "dock", new Tracked<>(72, List.of()),
                " ", new Tracked<>(40, List.of()));

        Set<ConstraintViolation<StorageUnit>> violations = validator.validate(unit);

        assertEquals(Set.of("humidityByZone<K>[ ].<map key>: must not be blank",
                "humidityByZone[dock].<map value>.<current reading>: must be less than or equal to 60"),
                describe(violations));
    }

    @Test
    void auditValidatorOverridesTheExtractorToCheckEveryHistoricalReading() {
        StorageUnit unit = compliantUnit();
        unit.temperature = new Tracked<>(new BigDecimal("5.0"),
                List.of(new BigDecimal("4.5"), new BigDecimal("1.0"), new BigDecimal("12.0")));
        Validator auditValidator = factory.usingContext()
                .addValueExtractor(new ReadingHistoryExtractor())
                .getValidator();

        Set<ConstraintViolation<StorageUnit>> dailyCheck = validator.validate(unit);
        Set<ConstraintViolation<StorageUnit>> audit = auditValidator.validate(unit);

        assertTrue(dailyCheck.isEmpty(), () -> "the current reading is valid: " + describe(dailyCheck));
        assertEquals(Set.of("temperature[1].<historical reading>: must be greater than or equal to 2.0",
                "temperature[2].<historical reading>: must be less than or equal to 8.0"), describe(audit));
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void exposesContainerElementConstraintsAndUnwrappingThroughTheMetadataApi() {
        PropertyDescriptor temperature = validator.getConstraintsForClass(StorageUnit.class)
                .getConstraintsForProperty("temperature");
        PropertyDescriptor barcode = validator.getConstraintsForClass(StorageUnit.class)
                .getConstraintsForProperty("barcode");

        Set<ContainerElementTypeDescriptor> elementTypes = temperature.getConstrainedContainerElementTypes();
        ContainerElementTypeDescriptor reading = elementTypes.iterator().next();
        Map<String, ValidateUnwrappedValue> barcodeUnwrapping = barcode.getConstraintDescriptors().stream()
                .collect(Collectors.toMap(
                        descriptor -> descriptor.getAnnotation().annotationType().getSimpleName(),
                        descriptor -> descriptor.getValueUnwrapping()));

        assertEquals(1, elementTypes.size());
        assertEquals(Tracked.class, reading.getContainerClass());
        assertEquals(0, reading.getTypeArgumentIndex());
        assertEquals(BigDecimal.class, reading.getElementClass());
        assertEquals(Set.of("DecimalMin", "DecimalMax"), reading.getConstraintDescriptors().stream()
                .map(descriptor -> descriptor.getAnnotation().annotationType().getSimpleName())
                .collect(Collectors.toSet()));
        assertEquals(Map.of("NotNull", ValidateUnwrappedValue.SKIP,
                "Size", ValidateUnwrappedValue.DEFAULT,
                "Pattern", ValidateUnwrappedValue.UNWRAP), barcodeUnwrapping);
    }

    @Test
    void rejectsAConstrainedContainerThatHasNoRegisteredExtractor() {
        try (ValidatorFactory bareFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .addValueExtractor(new BarcodeDigitsExtractor())
                .buildValidatorFactory()) {
            Validator bareValidator = bareFactory.getValidator();

            ValidationException failure = assertThrows(ValidationException.class,
                    () -> bareValidator.validate(compliantUnit()));

            assertTrue(failure.getMessage().contains(Tracked.class.getName()), failure::getMessage);
        }
    }

    private static StorageUnit compliantUnit() {
        StorageUnit unit = new StorageUnit();
        unit.unitId = "freezer-07";
        unit.temperature = new Tracked<>(new BigDecimal("4.2"), List.of(new BigDecimal("3.9")));
        unit.barcode = new Barcode("4006381333931");
        unit.reorderThreshold = Optional.of(25);
        unit.shelfLifeDays = OptionalInt.of(180);
        unit.humidityByZone = Map.of("dock", new Tracked<>(45, List.of(50, 48)));
        return unit;
    }

    private static <T> Set<String> describe(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** A sensor value together with the readings taken before it, oldest first. */
    static final class Tracked<T> {

        private final T current;
        private final List<T> history;

        Tracked(T current, List<T> history) {
            this.current = current;
            this.history = history;
        }

        T current() {
            return current;
        }

        List<T> history() {
            return history;
        }
    }

    /** A pallet label; only its digits carry meaning for validation. */
    static final class Barcode {

        private final String digits;

        Barcode(String digits) {
            this.digits = digits;
        }

        String digits() {
            return digits;
        }
    }

    /** Hands the current reading of a `Tracked` value to the validation engine. */
    static final class CurrentReadingExtractor implements ValueExtractor<Tracked<@ExtractedValue ?>> {

        @Override
        public void extractValues(Tracked<?> originalValue, ValueReceiver receiver) {
            receiver.value("<current reading>", originalValue.current());
        }
    }

    /** Hands every earlier reading of a `Tracked` value to the validation engine, by position. */
    static final class ReadingHistoryExtractor implements ValueExtractor<Tracked<@ExtractedValue ?>> {

        @Override
        public void extractValues(Tracked<?> originalValue, ValueReceiver receiver) {
            List<?> history = originalValue.history();
            for (int index = 0; index < history.size(); index++) {
                receiver.indexedValue("<historical reading>", index, history.get(index));
            }
        }
    }

    /** Unwraps the digits of a `Barcode` so that string constraints apply to them. */
    @UnwrapByDefault
    static final class BarcodeDigitsExtractor implements ValueExtractor<@ExtractedValue(type = String.class) Barcode> {

        @Override
        public void extractValues(Barcode originalValue, ValueReceiver receiver) {
            receiver.value(null, originalValue.digits());
        }
    }

    /** One refrigerated storage unit as the warehouse application records it. */
    static final class StorageUnit {

        @NotBlank
        String unitId;

        Tracked<@DecimalMin("2.0") @DecimalMax("8.0") BigDecimal> temperature;

        @NotNull(payload = Unwrapping.Skip.class)
        @Size(min = 8, max = 13)
        @Pattern(regexp = "\\d+", payload = Unwrapping.Unwrap.class)
        Barcode barcode;

        Optional<@Positive Integer> reorderThreshold;

        @Min(1)
        @Max(365)
        OptionalInt shelfLifeDays;

        Map<@NotBlank String, Tracked<@Max(60) Integer>> humidityByZone;
    }
}
