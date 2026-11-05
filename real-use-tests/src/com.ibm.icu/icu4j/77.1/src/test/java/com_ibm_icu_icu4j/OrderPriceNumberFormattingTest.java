/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.number.Precision;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.MeasureUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's number-formatting subsystem for prices and discounts shown in an order summary.
public class OrderPriceNumberFormattingTest {
    @Test
    public void formatsPricesAndDiscountsForAnOrderSummary() {
        OrderSummary orderSummary = new OrderSummary();

        assertEquals("$1,234.50", orderSummary.formatPrice(new BigDecimal("1234.5")));
        assertEquals("12.5%", orderSummary.formatDiscount(new BigDecimal("0.125")));
    }

    private static final class OrderSummary {
        private final LocalizedNumberFormatter priceFormatter = NumberFormatter.withLocale(Locale.US)
                .unit(Currency.getInstance("USD"))
                .precision(Precision.fixedFraction(2));
        private final LocalizedNumberFormatter discountFormatter = NumberFormatter.withLocale(Locale.US)
                .unit(MeasureUnit.PERCENT)
                .precision(Precision.fixedFraction(1));

        private String formatPrice(BigDecimal price) {
            return priceFormatter.format(price).toString();
        }

        private String formatDiscount(BigDecimal discountRate) {
            BigDecimal discountPercent = discountRate.movePointRight(2);
            return discountFormatter.format(discountPercent).toString();
        }
    }
}
