/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Traversable resolution: an application `TraversableResolver` deciding which properties of a
 * JPA entity graph are reachable and which associations are cascaded, set on the factory or per
 * validator, consulted by `validate` and `validateProperty`, with its failures wrapped by the
 * validator and the default JPA-aware resolver traversing everything without a provider.
 *
 * A procurement service validates purchase orders loaded by its persistence layer. Unloaded lazy
 * state must not be touched, because reading it would hit the database, and the supplier is a
 * shared aggregate the order does not own, so its own constraints are not the order's concern.
 * The service plugs in a resolver that knows the load state of each entity and the ownership of
 * each association, and asserts the violations a client of the order endpoint would receive.
 */
public class PurchaseOrderTraversableResolverTest {

    private ValidatorFactory defaultFactory;

    @BeforeEach
    void createFactory() {
        defaultFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .buildValidatorFactory();
    }

    @AfterEach
    void closeFactory() {
        defaultFactory.close();
    }

    @Test
    void defaultJpaAwareResolverTraversesTheWholeGraphWithoutAProvider() {
        PurchaseOrder order = invalidOrder();
        order.unloaded.add("notes");
        order.supplier.unloaded.add("name");

        Set<ConstraintViolation<PurchaseOrder>> violations = defaultFactory.getValidator().validate(order);

        assertNotNull(defaultFactory.getTraversableResolver());
        Map<String, String> expected = new TreeMap<>();
        expected.put("lines[0].quantity", "must be greater than or equal to 1");
        expected.put("notes", "size must be between 0 and 20");
        expected.put("supplier.name", "must not be blank");
        assertEquals(expected, describe(violations));
    }

    @Test
    void lazyAwareResolverSkipsUnloadedStateAndForeignAggregates() {
        LoadStateTraversableResolver resolver = new LoadStateTraversableResolver(ownedAssociations());
        PurchaseOrder order = invalidOrder();
        order.unloaded.add("notes");

        Set<ConstraintViolation<PurchaseOrder>> violations = defaultFactory.usingContext()
                .traversableResolver(resolver)
                .getValidator()
                .validate(order);

        Map<String, String> expected = new TreeMap<>();
        expected.put("lines[0].quantity", "must be greater than or equal to 1");
        assertEquals(expected, describe(violations));
        assertTrue(resolver.reachabilityChecks.contains("PurchaseOrder.notes@FIELD"), resolver.reachabilityChecks::toString);
        assertTrue(resolver.reachabilityChecks.contains("PurchaseOrder.supplier@FIELD"), resolver.reachabilityChecks::toString);
        assertTrue(resolver.reachabilityChecks.contains("OrderLine.quantity@FIELD"), resolver.reachabilityChecks::toString);
        assertTrue(resolver.reachabilityChecks.contains("OrderLine.lineTotal@METHOD"), resolver.reachabilityChecks::toString);
        assertTrue(resolver.cascadeChecks.contains("PurchaseOrder.supplier"), resolver.cascadeChecks::toString);
        assertTrue(resolver.cascadeChecks.contains("PurchaseOrder.lines"), resolver.cascadeChecks::toString);
        assertTrue(resolver.reachabilityChecks.stream().noneMatch(check -> check.startsWith("Supplier.")),
                resolver.reachabilityChecks::toString);
    }

    @Test
    void constraintsOnANonCascadedAssociationStillApply() {
        LoadStateTraversableResolver resolver = new LoadStateTraversableResolver(ownedAssociations());
        PurchaseOrder order = validOrder();
        order.supplier = null;

        Set<ConstraintViolation<PurchaseOrder>> violations = defaultFactory.usingContext()
                .traversableResolver(resolver)
                .getValidator()
                .validate(order);

        Map<String, String> expected = new TreeMap<>();
        expected.put("supplier", "must not be null");
        assertEquals(expected, describe(violations));
    }

    @Test
    void unloadedLazyAssociationIsNeitherCheckedNorCascaded() {
        Map<Class<?>, Set<String>> owned = ownedAssociations();
        owned.get(PurchaseOrder.class).add("supplier");
        LoadStateTraversableResolver resolver = new LoadStateTraversableResolver(owned);
        PurchaseOrder order = validOrder();
        order.supplier.name = " ";

        Validator validator = defaultFactory.usingContext().traversableResolver(resolver).getValidator();
        Map<String, String> loaded = describe(validator.validate(order));
        order.unloaded.add("supplier");
        order.supplier = null;
        Map<String, String> unloaded = describe(validator.validate(order));

        assertEquals(Map.of("supplier.name", "must not be blank"), loaded);
        assertEquals(Map.of(), unloaded);
    }

    @Test
    void validatePropertyConsultsTheResolverForTheRequestedProperty() {
        LoadStateTraversableResolver resolver = new LoadStateTraversableResolver(ownedAssociations());
        PurchaseOrder order = invalidOrder();
        Validator validator = defaultFactory.usingContext().traversableResolver(resolver).getValidator();

        Set<ConstraintViolation<PurchaseOrder>> loaded = validator.validateProperty(order, "notes");
        order.unloaded.add("notes");
        Set<ConstraintViolation<PurchaseOrder>> unloaded = validator.validateProperty(order, "notes");

        assertEquals(1, loaded.size());
        ConstraintViolation<PurchaseOrder> violation = loaded.iterator().next();
        assertEquals("notes", violation.getPropertyPath().toString());
        assertEquals(order.notes, violation.getInvalidValue());
        assertSame(order, violation.getLeafBean());
        assertTrue(unloaded.isEmpty(), () -> "unexpected violations: " + describe(unloaded));
        assertTrue(resolver.reachabilityChecks.contains("PurchaseOrder.notes@FIELD"), resolver.reachabilityChecks::toString);
    }

    @Test
    void factoryLevelResolverAppliesToEveryValidatorItCreates() {
        LoadStateTraversableResolver resolver = new LoadStateTraversableResolver(ownedAssociations());
        PurchaseOrder order = invalidOrder();
        order.lines.get(0).unloaded.add("quantity");

        try (ValidatorFactory factory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .defaultLocale(Locale.ENGLISH)
                .traversableResolver(resolver)
                .buildValidatorFactory()) {
            Set<ConstraintViolation<PurchaseOrder>> violations = factory.getValidator().validate(order);

            assertSame(resolver, factory.getTraversableResolver());
            Map<String, String> expected = new TreeMap<>();
            expected.put("notes", "size must be between 0 and 20");
            assertEquals(expected, describe(violations));
        }
    }

    @Test
    void wrapsAFailingResolverInAValidationException() {
        TraversableResolver failing = new FailingTraversableResolver();
        Validator validator = defaultFactory.usingContext().traversableResolver(failing).getValidator();

        ValidationException exception = assertThrows(ValidationException.class, () -> validator.validate(validOrder()));

        assertTrue(exception.getMessage().contains("TraversableResolver.isReachable()"), exception::getMessage);
        assertTrue(exception.getCause() instanceof IllegalStateException, () -> String.valueOf(exception.getCause()));
        assertEquals("persistence context is closed", exception.getCause().getMessage());
    }

    private static Map<Class<?>, Set<String>> ownedAssociations() {
        Map<Class<?>, Set<String>> owned = new HashMap<>();
        owned.put(PurchaseOrder.class, new HashSet<>(Set.of("lines")));
        return owned;
    }

    private static PurchaseOrder validOrder() {
        Supplier supplier = new Supplier();
        supplier.id = 7L;
        supplier.name = "Northwind Traders";

        OrderLine line = new OrderLine();
        line.id = 100L;
        line.sku = "BOLT-M8";
        line.quantity = 4;
        line.unitPriceCents = 25;

        PurchaseOrder order = new PurchaseOrder();
        order.id = 1L;
        order.reference = "PO-2024-0001";
        order.notes = "deliver to dock 3";
        order.supplier = supplier;
        order.lines.add(line);
        return order;
    }

    private static PurchaseOrder invalidOrder() {
        PurchaseOrder order = validOrder();
        order.notes = "deliver to the north loading dock before noon";
        order.supplier.name = "";
        order.lines.get(0).quantity = 0;
        return order;
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        Map<String, String> described = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            described.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return described;
    }

    /// Persistence-layer view of an entity: the names of properties not fetched yet.
    interface LazyLoadable {

        Set<String> unloadedProperties();
    }

    @Entity
    static class PurchaseOrder implements LazyLoadable {

        @Id
        Long id;

        @NotBlank
        String reference;

        @Size(max = 20)
        String notes;

        @NotNull
        @Valid
        @ManyToOne(fetch = FetchType.LAZY)
        Supplier supplier;

        @Valid
        @OneToMany
        List<OrderLine> lines = new ArrayList<>();

        final Set<String> unloaded = new HashSet<>();

        @Override
        public Set<String> unloadedProperties() {
            return unloaded;
        }
    }

    @Entity
    static class Supplier implements LazyLoadable {

        @Id
        Long id;

        @NotBlank
        String name;

        final Set<String> unloaded = new HashSet<>();

        @Override
        public Set<String> unloadedProperties() {
            return unloaded;
        }
    }

    @Entity
    static class OrderLine implements LazyLoadable {

        @Id
        Long id;

        @NotBlank
        String sku;

        @Min(1)
        int quantity;

        @Min(0)
        long unitPriceCents;

        final Set<String> unloaded = new HashSet<>();

        @Min(0)
        public long getLineTotal() {
            return quantity * unitPriceCents;
        }

        @Override
        public Set<String> unloadedProperties() {
            return unloaded;
        }
    }

    /// Reaches only loaded properties and cascades only into associations the entity owns.
    static class LoadStateTraversableResolver implements TraversableResolver {

        private final Map<Class<?>, Set<String>> ownedAssociations;
        final Set<String> reachabilityChecks = new TreeSet<>();
        final Set<String> cascadeChecks = new TreeSet<>();

        LoadStateTraversableResolver(Map<Class<?>, Set<String>> ownedAssociations) {
            this.ownedAssociations = ownedAssociations;
        }

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                Path pathToTraversableObject, ElementType elementType) {
            reachabilityChecks.add(traversableObject.getClass().getSimpleName() + "." + traversableProperty.getName()
                    + "@" + elementType);
            return !(traversableObject instanceof LazyLoadable)
                    || !((LazyLoadable) traversableObject).unloadedProperties().contains(traversableProperty.getName());
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                Path pathToTraversableObject, ElementType elementType) {
            cascadeChecks.add(traversableObject.getClass().getSimpleName() + "." + traversableProperty.getName());
            return ownedAssociations.getOrDefault(traversableObject.getClass(), Set.of())
                    .contains(traversableProperty.getName());
        }
    }

    /// Simulates a resolver whose persistence context was closed before validation ran.
    static class FailingTraversableResolver implements TraversableResolver {

        @Override
        public boolean isReachable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                Path pathToTraversableObject, ElementType elementType) {
            throw new IllegalStateException("persistence context is closed");
        }

        @Override
        public boolean isCascadable(Object traversableObject, Path.Node traversableProperty, Class<?> rootBeanType,
                Path pathToTraversableObject, ElementType elementType) {
            throw new IllegalStateException("persistence context is closed");
        }
    }
}
