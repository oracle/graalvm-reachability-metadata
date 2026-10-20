/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_validator.hibernate_validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.money.MonetaryAmount;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.constraints.Currency;
import org.javamoney.moneta.FastMoney;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Money and Currency API integration: Hibernate's `@Currency` constraint and the built-in
 * numeric constraints (`@DecimalMin`, `@DecimalMax`, `@Max`, `@Digits`, `@PositiveOrZero`,
 * `@Negative`) applied to JSR 354 `MonetaryAmount` fields and getters backed by Moneta's
 * `Money` and `FastMoney` implementations.
 *
 * An online marketplace validates a seller's listing before it goes live: the asking price must
 * be quoted in a currency the marketplace settles in and respect a minimum price, the shipping
 * fee cannot be negative, a seller discount is booked as a negative adjustment, the buy-now
 * price must fit the payment processor's amount format and the total a buyer pays is capped.
 * The scenario asserts the messages the seller would see on the listing form.
 */
public class MarketplaceListingMoneyValidationTest {

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
    void acceptsListingInSettledCurrency() {
        Listing listing = new Listing(
                Money.of(new BigDecimal("249.90"), "EUR"),
                FastMoney.of(new BigDecimal("12.50"), "EUR"),
                Money.of(new BigDecimal("-20.00"), "EUR"),
                Money.of(new BigDecimal("299.00"), "EUR"),
                Money.of(new BigDecimal("800"), "EUR"));

        Set<ConstraintViolation<Listing>> violations = validator.validate(listing);

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + messagesByPath(violations));
        assertEquals(Money.of(new BigDecimal("242.40"), "EUR"), listing.getTotal());
    }

    @Test
    void rejectsPriceQuotedInUnsettledCurrency() {
        Listing listing = new Listing(
                Money.of(new BigDecimal("180.00"), "GBP"),
                FastMoney.of(new BigDecimal("8.00"), "GBP"),
                Money.of(new BigDecimal("-5.00"), "GBP"),
                Money.of(new BigDecimal("220.00"), "GBP"),
                Money.of(new BigDecimal("500"), "GBP"));

        Set<ConstraintViolation<Listing>> violations = validator.validate(listing);

        assertEquals(Map.of("price", "invalid currency (must be one of [EUR, USD])"), messagesByPath(violations));
        ConstraintViolation<Listing> violation = violations.iterator().next();
        assertEquals(Money.of(new BigDecimal("180.00"), "GBP"), violation.getInvalidValue());
        assertEquals(Currency.class, violation.getConstraintDescriptor().getAnnotation().annotationType());
    }

    @Test
    void reportsEveryAmountOutsideItsBounds() {
        Listing listing = new Listing(
                Money.of(new BigDecimal("0.25"), "USD"),
                FastMoney.of(new BigDecimal("-3.00"), "USD"),
                Money.of(new BigDecimal("4.00"), "USD"),
                Money.of(new BigDecimal("1234567.891"), "USD"),
                Money.of(new BigDecimal("25000"), "USD"));

        Set<ConstraintViolation<Listing>> violations = validator.validate(listing);

        Map<String, String> expected = new TreeMap<>();
        expected.put("price", "must be greater than or equal to 0.50");
        expected.put("shippingFee", "must be greater than or equal to 0");
        expected.put("sellerDiscount", "must be less than 0");
        expected.put("buyNowPrice", "numeric value out of bounds (<6 digits>.<2 digits> expected)");
        expected.put("reservePrice", "must be less than or equal to 10000");
        assertEquals(expected, messagesByPath(violations));
    }

    @Test
    void capsTheTotalComputedFromMoneyArithmetic() {
        Listing listing = new Listing(
                Money.of(new BigDecimal("4990.00"), "USD"),
                FastMoney.of(new BigDecimal("45.00"), "USD"),
                Money.of(new BigDecimal("-10.00"), "USD"),
                Money.of(new BigDecimal("5200.00"), "USD"),
                Money.of(new BigDecimal("4500"), "USD"));

        Set<ConstraintViolation<Listing>> violations = validator.validate(listing);

        assertEquals(Map.of("total", "must be less than 5000.00"), messagesByPath(violations));
        assertEquals(Money.of(new BigDecimal("5025.00"), "USD"), violations.iterator().next().getInvalidValue());
    }

    @Test
    void checksAFormFieldBeforeTheListingExists() {
        Set<ConstraintViolation<Listing>> swissFrancs = validator.validateValue(
                Listing.class, "price", Money.of(new BigDecimal("99.00"), "CHF"));
        Set<ConstraintViolation<Listing>> dollars = validator.validateValue(
                Listing.class, "price", FastMoney.of(new BigDecimal("99.00"), "USD"));
        Set<ConstraintViolation<Listing>> missing = validator.validateValue(Listing.class, "price", null);

        assertEquals(Map.of("price", "invalid currency (must be one of [EUR, USD])"), messagesByPath(swissFrancs));
        assertTrue(dollars.isEmpty(), () -> "unexpected violations: " + messagesByPath(dollars));
        assertEquals(Map.of("price", "must not be null"), messagesByPath(missing));
    }

    private static <T> Map<String, String> messagesByPath(Set<ConstraintViolation<T>> violations) {
        Map<String, String> messages = new TreeMap<>();
        for (ConstraintViolation<T> violation : violations) {
            messages.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return messages;
    }

    /** A seller's listing as the marketplace stores it before publication. */
    public static class Listing {

        @NotNull
        @Currency({"EUR", "USD"})
        @DecimalMin("0.50")
        private final MonetaryAmount price;

        @PositiveOrZero
        private final MonetaryAmount shippingFee;

        @Negative
        private final MonetaryAmount sellerDiscount;

        @Digits(integer = 6, fraction = 2)
        private final MonetaryAmount buyNowPrice;

        @Max(10000)
        private final MonetaryAmount reservePrice;

        public Listing(MonetaryAmount price, MonetaryAmount shippingFee, MonetaryAmount sellerDiscount,
                MonetaryAmount buyNowPrice, MonetaryAmount reservePrice) {
            this.price = price;
            this.shippingFee = shippingFee;
            this.sellerDiscount = sellerDiscount;
            this.buyNowPrice = buyNowPrice;
            this.reservePrice = reservePrice;
        }

        /** The amount a buyer pays: price plus shipping minus the seller's discount. */
        @DecimalMax(value = "5000.00", inclusive = false)
        public MonetaryAmount getTotal() {
            return price.add(shippingFee).add(sellerDiscount);
        }
    }
}
