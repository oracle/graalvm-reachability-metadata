/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.RoundedMoney;
import org.javamoney.moneta.function.MonetaryOperators;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's rounded-money subsystem for a service charge split into customer installments.
 */
public class RoundedMoneyInstallmentTest {
    @Test
    @Timeout(60)
    public void appliesPercentageAndSplitsRoundedCharge() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        RoundedMoney subtotal = RoundedMoney.of(
                new BigDecimal("100.00"), usd, twoDecimalPlaces());

        RoundedMoney serviceCharge = subtotal.with(MonetaryOperators.percent(new BigDecimal("8.25")));
        RoundedMoney total = subtotal.add(serviceCharge);
        RoundedMoney[] installment = total.divideAndRemainder(3);

        assertThat(serviceCharge).isEqualTo(RoundedMoney.of(new BigDecimal("8.25"), usd));
        assertThat(total).isEqualTo(RoundedMoney.of(new BigDecimal("108.25"), usd));
        assertThat(installment[0]).isEqualTo(RoundedMoney.of(new BigDecimal("36.00"), usd));
        assertThat(installment[1]).isEqualTo(RoundedMoney.of(new BigDecimal("0.25"), usd));
        assertThat(installment[0].multiply(3).add(installment[1])).isEqualTo(total);
        assertThat(total.getCurrency()).isEqualTo(usd);
        assertThat(total.getNumber().numberValue(BigDecimal.class))
                .isEqualByComparingTo("108.25");
    }

    private static javax.money.MonetaryOperator twoDecimalPlaces() {
        return MonetaryOperators.rounding(RoundingMode.HALF_UP, 2);
    }
}
