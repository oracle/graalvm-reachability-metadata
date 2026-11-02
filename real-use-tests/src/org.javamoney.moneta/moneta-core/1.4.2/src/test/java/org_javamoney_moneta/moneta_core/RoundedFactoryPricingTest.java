/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryRoundedFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's precision-and-scale rounded factory subsystem for catalog pricing.
 */
public class RoundedFactoryPricingTest {
    @Test
    @Timeout(60)
    public void roundsCatalogPricesBeforeBuildingInvoiceTotal() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Catalog catalog = new Catalog(usd);

        Money coffee = catalog.price(new BigDecimal("19.995"));
        Money pastry = catalog.price(new BigDecimal("4.567"));
        Money invoiceTotal = Money.from(coffee.add(pastry));

        assertThat(coffee.getNumber().numberValue(BigDecimal.class))
                .isEqualByComparingTo("20.00");
        assertThat(pastry.getNumber().numberValue(BigDecimal.class))
                .isEqualByComparingTo("4.57");
        assertThat(invoiceTotal.getCurrency()).isEqualTo(usd);
        assertThat(invoiceTotal.getNumber().numberValue(BigDecimal.class))
                .isEqualByComparingTo("24.57");
    }

    private static final class Catalog {
        private final CurrencyUnit currency;
        private final MonetaryRoundedFactory priceFactory = MonetaryRoundedFactory
                .withRoundingMode(RoundingMode.HALF_UP)
                .withPrecision(6)
                .withScale(2)
                .build();

        private Catalog(CurrencyUnit currency) {
            this.currency = currency;
        }

        private Money price(BigDecimal rawPrice) {
            MonetaryAmount rawAmount = priceFactory.create(rawPrice, currency);
            MonetaryAmount roundedPrice = rawAmount.with(priceFactory.getRoundingOperator());
            return Money.from(roundedPrice);
        }
    }
}
