/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.metadata.BeanDescriptor;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.PropertyDescriptor;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.defs.MinDef;
import org.hibernate.validator.cfg.defs.NotBlankDef;
import org.hibernate.validator.spi.properties.ConstrainableExecutable;
import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;
import org.junit.jupiter.api.Test;

/**
 * Getter property selection: an application `GetterPropertySelectionStrategy` that makes
 * fluent, prefix-free accessors (`sku()`) count as bean properties next to JavaBeans getters,
 * so their constraints are validated, inherited from an interface, cascaded, reachable by
 * `validateProperty` and `validateValue`, exposed through the bean metadata, and targeted by
 * programmatic `getter(...)` mappings, where the default strategy ignores them.
 *
 * A warehouse management system models stock lines as immutable value objects with fluent
 * accessors and screens them before a shipment manifest is released to a carrier.
 */
public class WarehouseStockFluentGetterSelectionTest {

    private static final String NOT_BLANK = "{jakarta.validation.constraints.NotBlank.message}";
    private static final String MIN = "{jakarta.validation.constraints.Min.message}";
    private static final String PATTERN = "{jakarta.validation.constraints.Pattern.message}";
    private static final String NOT_NULL = "{jakarta.validation.constraints.NotNull.message}";

    @Test
    void defaultStrategyOnlyValidatesJavaBeansGetters() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            PalletStock broken = new PalletStock(" ", 0, "aisle-7", "");

            Map<String, String> violations = templatesByPath(validator.validate(broken));

            assertEquals(Map.of("warehouseCode", NOT_BLANK), violations);
            BeanDescriptor descriptor = validator.getConstraintsForClass(PalletStock.class);
            assertEquals(Set.of("warehouseCode"), propertyNames(descriptor));
        }
    }

    @Test
    void fluentStrategyValidatesFluentAccessorsInheritedFromTheInterface() {
        try (ValidatorFactory factory = fluentConfiguration().buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            PalletStock broken = new PalletStock(" ", 0, "aisle-7", "");
            PalletStock valid = new PalletStock("SKU-1001", 40, "B-12", "DUB1");

            Set<ConstraintViolation<PalletStock>> violations = validator.validate(broken);

            assertEquals(
                    Map.of(
                            "sku", NOT_BLANK,
                            "quantity", MIN,
                            "bin", PATTERN,
                            "warehouseCode", NOT_BLANK),
                    templatesByPath(violations));
            ConstraintViolation<PalletStock> quantity = violationAt(violations, "quantity");
            assertEquals(0, quantity.getInvalidValue());
            assertEquals(broken, quantity.getLeafBean());
            assertEquals("aisle-7", violationAt(violations, "bin").getInvalidValue());
            assertTrue(validator.validate(valid).isEmpty());
        }
    }

    @Test
    void fluentStrategyCascadesThroughFluentContainerAccessors() {
        try (ValidatorFactory factory = fluentConfiguration().buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            Shipment shipment = new Shipment(
                    null,
                    List.of(
                            new PalletStock("SKU-1001", 40, "B-12", "DUB1"),
                            new PalletStock("SKU-2002", -3, "C-07", "DUB1"),
                            new PalletStock("", 12, "D-01", "DUB1")));

            Set<ConstraintViolation<Shipment>> violations = validator.validate(shipment);

            assertEquals(
                    Map.of(
                            "carrier", NOT_NULL,
                            "lines[1].quantity", MIN,
                            "lines[2].sku", NOT_BLANK),
                    templatesByPath(violations));
            ConstraintViolation<Shipment> negative = violationAt(violations, "lines[1].quantity");
            assertEquals(shipment, negative.getRootBean());
            assertEquals(shipment.lines().get(1), negative.getLeafBean());
            assertEquals(-3, negative.getInvalidValue());
        }
    }

    @Test
    void fluentPropertiesCanBeValidatedOneByOne() {
        try (ValidatorFactory factory = fluentConfiguration().buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            PalletStock line = new PalletStock("SKU-1001", 40, "bulk", "DUB1");

            Set<ConstraintViolation<PalletStock>> bin = validator.validateProperty(line, "bin");
            Set<ConstraintViolation<PalletStock>> quantity =
                    validator.validateProperty(line, "quantity");
            Set<ConstraintViolation<PalletStock>> proposedQuantity =
                    validator.validateValue(PalletStock.class, "quantity", 0);

            assertEquals(Map.of("bin", PATTERN), templatesByPath(bin));
            assertTrue(quantity.isEmpty());
            assertEquals(Map.of("quantity", MIN), templatesByPath(proposedQuantity));
            assertEquals(0, proposedQuantity.iterator().next().getInvalidValue());
        }
    }

    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void beanMetadataListsFluentPropertiesWithTheirConstraints() {
        try (ValidatorFactory factory = fluentConfiguration().buildValidatorFactory()) {
            Validator validator = factory.getValidator();

            BeanDescriptor stock = validator.getConstraintsForClass(PalletStock.class);
            BeanDescriptor shipment = validator.getConstraintsForClass(Shipment.class);

            assertEquals(Set.of("sku", "quantity", "bin", "warehouseCode"), propertyNames(stock));
            PropertyDescriptor quantity = stock.getConstraintsForProperty("quantity");
            assertNotNull(quantity);
            ConstraintDescriptor<?> min = quantity.getConstraintDescriptors().iterator().next();
            assertEquals(Min.class, min.getAnnotation().annotationType());
            assertEquals(1L, min.getAttributes().get("value"));
            assertNull(stock.getConstraintsForProperty("hazardous"));
            PropertyDescriptor lines = shipment.getConstraintsForProperty("lines");
            assertTrue(lines.isCascaded());
            assertEquals(List.class, lines.getElementClass());
        }
    }

    @Test
    void programmaticGetterMappingsResolveFluentAndPrefixedAccessors() {
        HibernateValidatorConfiguration configuration = fluentConfiguration();
        ConstraintMapping mapping = configuration.createConstraintMapping();
        mapping.type(ReturnedStock.class)
                .getter("sku")
                .constraint(new NotBlankDef())
                .getter("restockFee")
                .constraint(new MinDef().value(0));

        configuration.addMapping(mapping);

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            Validator validator = factory.getValidator();
            ReturnedStock damaged = new ReturnedStock("", -5);
            ReturnedStock accepted = new ReturnedStock("SKU-3003", 15);

            Set<ConstraintViolation<ReturnedStock>> violations = validator.validate(damaged);

            assertEquals(Map.of("sku", NOT_BLANK, "restockFee", MIN), templatesByPath(violations));
            assertEquals(-5, violationAt(violations, "restockFee").getInvalidValue());
            assertTrue(validator.validate(accepted).isEmpty());
        }
    }

    private static HibernateValidatorConfiguration fluentConfiguration() {
        return Validation
                .byProvider(HibernateValidator.class)
                .configure()
                .getterPropertySelectionStrategy(new FluentAccessorStrategy());
    }

    private static <T> Map<String, String> templatesByPath(Set<ConstraintViolation<T>> violations) {
        Map<String, String> templates = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            String previous = templates.put(
                    violation.getPropertyPath().toString(), violation.getMessageTemplate());
            assertNull(previous, "one violation per property path");
        }
        return templates;
    }

    private static <T> ConstraintViolation<T> violationAt(
            Set<ConstraintViolation<T>> violations, String path) {
        return violations.stream()
                .filter(violation -> violation.getPropertyPath().toString().equals(path))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> propertyNames(BeanDescriptor descriptor) {
        Set<String> names = new TreeSet<>();
        for (PropertyDescriptor property : descriptor.getConstrainedProperties()) {
            names.add(property.getPropertyName());
        }
        return names;
    }

    /// Treats every public no-argument, non-void method as a property accessor: fluent
    /// `name()` maps to `name`, while `getName()` and `isName()` keep their JavaBeans meaning.
    public static final class FluentAccessorStrategy implements GetterPropertySelectionStrategy {

        private static final Set<String> OBJECT_METHODS =
                Set.of("toString", "hashCode", "getClass", "clone");

        @Override
        public Optional<String> getProperty(ConstrainableExecutable executable) {
            String name = executable.getName();
            if (executable.getParameterTypes().length > 0
                    || executable.getReturnType() == void.class
                    || OBJECT_METHODS.contains(name)) {
                return Optional.empty();
            }
            if (name.startsWith("get") && name.length() > 3) {
                return Optional.of(decapitalize(name.substring(3)));
            }
            if (name.startsWith("is") && name.length() > 2
                    && executable.getReturnType() == boolean.class) {
                return Optional.of(decapitalize(name.substring(2)));
            }
            return Optional.of(name);
        }

        @Override
        public Set<String> getGetterMethodNameCandidates(String propertyName) {
            String capitalized =
                    Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            return new LinkedHashSet<>(
                    Arrays.asList(propertyName, "get" + capitalized, "is" + capitalized));
        }

        private static String decapitalize(String name) {
            return Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
    }

    /// Contract every stock line honours, wherever it was received.
    public interface StockLine {

        @NotBlank
        String sku();

        @Min(1)
        int quantity();
    }

    /// A palletised stock line with fluent accessors and one legacy JavaBeans getter.
    public static final class PalletStock implements StockLine {

        private final String sku;
        private final int quantity;
        private final String bin;
        private final String warehouseCode;

        public PalletStock(String sku, int quantity, String bin, String warehouseCode) {
            this.sku = sku;
            this.quantity = quantity;
            this.bin = bin;
            this.warehouseCode = warehouseCode;
        }

        @Override
        public String sku() {
            return sku;
        }

        @Override
        public int quantity() {
            return quantity;
        }

        @Pattern(regexp = "[A-Z]-\\d{2}")
        public String bin() {
            return bin;
        }

        @NotBlank
        public String getWarehouseCode() {
            return warehouseCode;
        }

        public boolean hazardous() {
            return false;
        }
    }

    /// A manifest handed to a carrier once every line passes validation.
    public static final class Shipment {

        private final String carrier;
        private final List<StockLine> lines;

        public Shipment(String carrier, List<StockLine> lines) {
            this.carrier = carrier;
            this.lines = lines;
        }

        @NotNull
        public String carrier() {
            return carrier;
        }

        @Valid
        public List<StockLine> lines() {
            return lines;
        }
    }

    /// A customer return, constrained only through a programmatic mapping.
    public static final class ReturnedStock {

        private final String sku;
        private final int restockFee;

        public ReturnedStock(String sku, int restockFee) {
            this.sku = sku;
            this.restockFee = restockFee;
        }

        public String sku() {
            return sku;
        }

        public int getRestockFee() {
            return restockFee;
        }
    }
}
