/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.format.MonetaryAmountDecimalFormatBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.format.MonetaryAmountFormat;
import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's locale-aware decimal formatting subsystem for a product price tag.
 */
public class LocalizedPriceTagTest {
    @Test
    @Timeout(60)
    public void formatsAndParsesCatalogPriceForStoreLocale() {
        Locale previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            CurrencyUnit usd = Monetary.getCurrency("USD");
            Money catalogPrice = Money.of(new BigDecimal("12345.67"), usd);
            MonetaryAmountFormat priceFormat = MonetaryAmountDecimalFormatBuilder
                    .of("$#,##0.00", Locale.US)
                    .withCurrencyUnit(usd)
                    .build();

            String priceTag = priceFormat.format(catalogPrice);
            Money parsedPrice = Money.from(priceFormat.parse(priceTag));

            assertThat(priceTag).isEqualTo("$12,345.67");
            assertThat(parsedPrice).isEqualTo(catalogPrice);
            assertThat(parsedPrice.getCurrency()).isEqualTo(usd);
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
