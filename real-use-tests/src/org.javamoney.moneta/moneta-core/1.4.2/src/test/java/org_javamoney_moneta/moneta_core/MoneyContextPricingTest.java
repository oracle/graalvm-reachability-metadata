/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryContext;
import javax.money.MonetaryContextBuilder;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's monetary-context precision subsystem for a tax calculation with an application-defined amount policy.
 */
public class MoneyContextPricingTest {
    @Test
    @Timeout(60)
    public void appliesConfiguredPrecisionToCatalogPrices() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        MonetaryContext pricingContext = MonetaryContextBuilder.of()
                .setAmountType(Money.class)
                .setPrecision(6)
                .setMaxScale(2)
                .setFixedScale(true)
                .build();

        Money catalogPrice = Money.of(new BigDecimal("1234.5678"), usd, pricingContext);
        Money priceWithTax = catalogPrice.multiply(new BigDecimal("1.25"));

        assertThat(catalogPrice).isEqualTo(Money.of(new BigDecimal("1234.57"), usd, pricingContext));
        assertThat(priceWithTax).isEqualTo(Money.of(new BigDecimal("1543.21"), usd, pricingContext));
        assertThat(priceWithTax.getContext().getAmountType()).isEqualTo(Money.class);
        assertThat(priceWithTax.getContext().getPrecision()).isEqualTo(6);
        assertThat(priceWithTax.getContext().getMaxScale()).isEqualTo(2);
        assertThat(priceWithTax.getContext().isFixedScale()).isTrue();
        assertThat(priceWithTax.getNumber().getScale()).isEqualTo(2);
    }
}
