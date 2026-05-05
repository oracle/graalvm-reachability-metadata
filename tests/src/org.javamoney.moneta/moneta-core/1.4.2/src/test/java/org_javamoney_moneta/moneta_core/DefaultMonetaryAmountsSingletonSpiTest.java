/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.FastMoney;
import org.javamoney.moneta.spi.DefaultMonetaryAmountsSingletonSpi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultMonetaryAmountsSingletonSpiTest {
    private static final String DEFAULT_AMOUNT_TYPE_PROPERTY = "org.javamoney.moneta.Money.defaults.amountType";

    @Test
    public void getDefaultAmountTypeLoadsConfiguredAmountClass() {
        String previousValue = System.getProperty(DEFAULT_AMOUNT_TYPE_PROPERTY);
        try {
            System.setProperty(DEFAULT_AMOUNT_TYPE_PROPERTY, FastMoney.class.getName());

            DefaultMonetaryAmountsSingletonSpi amounts = new DefaultMonetaryAmountsSingletonSpi();

            assertThat(amounts.getDefaultAmountType()).isEqualTo(FastMoney.class);
            assertThat(amounts.getAmountTypes()).contains(FastMoney.class);
        } finally {
            if (previousValue == null) {
                System.clearProperty(DEFAULT_AMOUNT_TYPE_PROPERTY);
            } else {
                System.setProperty(DEFAULT_AMOUNT_TYPE_PROPERTY, previousValue);
            }
        }
    }
}
