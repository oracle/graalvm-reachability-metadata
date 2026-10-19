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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cascaded validation of an object graph: `@Valid` on a nested bean field and on the elements
 * of `List` and `Map` containers, with built-in constraints on fields, getters and container
 * element types, reported through property paths, invalid values, leaf and root beans.
 *
 * A web shop validates a checkout order before it is placed. The order carries a customer with
 * a shipping address, a list of line items and a map of coupon codes to discounts. The shop
 * renders each violation next to the form field its property path names, so the scenario
 * asserts the exact path, the rejected value and the English message of every violation.
 */
public class CheckoutOrderCascadedValidationTest {

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
    void acceptsCompleteOrder() {
        CheckoutOrder order = validOrder();

        Set<ConstraintViolation<CheckoutOrder>> violations = validator.validate(order);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + describe(violations));
    }

    @Test
    void reportsViolationsAcrossTheWholeGraph() {
        CheckoutOrder order = validOrder();
        order.customer.email = "not-an-email";
        order.customer.shippingAddress.postalCode = "12";
        order.lineItems.get(1).quantity = 0;
        order.lineItems.get(1).sku = "    ";
        order.coupons.put("", 5);
        order.coupons.put("SPRING", -10);

        Set<ConstraintViolation<CheckoutOrder>> violations = validator.validate(order);

        Map<String, String> expected = new TreeMap<>();
        expected.put("customer.email", "must be a well-formed email address");
        expected.put("customer.shippingAddress.postalCode", "must match \"[0-9]{5}\"");
        expected.put("lineItems[1].quantity", "must be greater than or equal to 1");
        expected.put("lineItems[1].sku", "must not be blank");
        expected.put("coupons<K>[].<map key>", "must not be blank");
        expected.put("coupons[SPRING].<map value>", "must be greater than 0");
        assertEquals(expected, describe(violations));
    }

    @Test
    void exposesInvalidValueLeafBeanAndRootBean() {
        CheckoutOrder order = validOrder();
        LineItem brokenItem = order.lineItems.get(0);
        brokenItem.quantity = -3;

        Set<ConstraintViolation<CheckoutOrder>> violations = validator.validate(order);

        assertEquals(1, violations.size());
        ConstraintViolation<CheckoutOrder> violation = violations.iterator().next();
        assertEquals("lineItems[0].quantity", violation.getPropertyPath().toString());
        assertEquals(-3, violation.getInvalidValue());
        assertSame(order, violation.getRootBean());
        assertSame(brokenItem, violation.getLeafBean());
        assertSame(CheckoutOrder.class, violation.getRootBeanClass());
        assertEquals(Min.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
        assertEquals("{jakarta.validation.constraints.Min.message}", violation.getMessageTemplate());
    }

    @Test
    void validatesGetterConstraintsAndNullCascades() {
        CheckoutOrder order = validOrder();
        order.customer.shippingAddress = null;
        order.lineItems.clear();

        Set<ConstraintViolation<CheckoutOrder>> violations = validator.validate(order);

        Map<String, String> expected = new TreeMap<>();
        expected.put("customer.shippingAddress", "must not be null");
        expected.put("lineItems", "must not be empty");
        expected.put("totalQuantity", "must be greater than or equal to 1");
        assertEquals(expected, describe(violations));
    }

    @Test
    void validatesSinglePropertyWithoutCascading() {
        CheckoutOrder order = validOrder();
        order.customer.email = "broken";
        order.lineItems.add(new LineItem("TOOLONG-SKU-0000000000", 2));

        Set<ConstraintViolation<CheckoutOrder>> customerViolations =
                validator.validateProperty(order, "customer");
        Set<ConstraintViolation<LineItem>> skuViolations =
                validator.validateValue(LineItem.class, "sku", "TOOLONG-SKU-0000000000");

        assertTrue(customerViolations.isEmpty(), "validateProperty must not cascade into customer");
        assertEquals(Map.of("sku", "size must be between 3 and 12"), describe(skuViolations));
    }

    private static CheckoutOrder validOrder() {
        Address address = new Address();
        address.street = "Main Street 1";
        address.city = "Springfield";
        address.postalCode = "12345";

        Customer customer = new Customer();
        customer.name = "Ada Lovelace";
        customer.email = "ada@example.com";
        customer.shippingAddress = address;

        CheckoutOrder order = new CheckoutOrder();
        order.customer = customer;
        order.lineItems.add(new LineItem("BOOK-001", 2));
        order.lineItems.add(new LineItem("PEN-042", 5));
        order.coupons.put("WELCOME", 10);
        return order;
    }

    private static <T> Map<String, String> describe(Set<ConstraintViolation<T>> violations) {
        Map<String, String> byPath = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            byPath.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return byPath;
    }

    public static class CheckoutOrder {

        @NotNull
        @Valid
        Customer customer;

        @NotEmpty
        List<@Valid LineItem> lineItems = new ArrayList<>();

        Map<@NotBlank String, @Positive Integer> coupons = new LinkedHashMap<>();

        @Min(1)
        public int getTotalQuantity() {
            return lineItems.stream().mapToInt(item -> item.quantity).sum();
        }
    }

    public static class Customer {

        @NotBlank
        String name;

        @NotNull
        @Email
        String email;

        @NotNull
        @Valid
        Address shippingAddress;
    }

    public static class Address {

        @NotBlank
        String street;

        @NotBlank
        String city;

        @NotNull
        @Pattern(regexp = "[0-9]{5}")
        String postalCode;
    }

    public static class LineItem {

        @NotBlank
        @Size(min = 3, max = 12)
        String sku;

        @Min(1)
        int quantity;

        LineItem(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }
}
