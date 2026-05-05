/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.FastMoney;
import org.javamoney.moneta.spi.DefaultMonetaryAmountsSingletonSpi;
import org.javamoney.moneta.spi.MonetaryConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultMonetaryAmountsSingletonSpiTest {
    private static final String DEFAULT_AMOUNT_TYPE_PROPERTY = "org.javamoney.moneta.Money.defaults.amountType";

    @Test
    public void getDefaultAmountTypeLoadsConfiguredAmountClass() {
        String previousValue = MonetaryConfig.setValue(DEFAULT_AMOUNT_TYPE_PROPERTY, FastMoney.class.getName());
        try {
            DefaultMonetaryAmountsSingletonSpi amounts = new DefaultMonetaryAmountsSingletonSpi();

            assertThat(amounts.getDefaultAmountType()).isEqualTo(FastMoney.class);
            assertThat(amounts.getAmountTypes()).contains(FastMoney.class);
        } finally {
            MonetaryConfig.setValue(DEFAULT_AMOUNT_TYPE_PROPERTY, previousValue);
        }
    }
}
