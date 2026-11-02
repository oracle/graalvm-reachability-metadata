/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.FastMoney;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's fixed-precision FastMoney subsystem for a minor-unit wallet.
 */
public class FastMoneyWalletTest {
    @Test
    @Timeout(60)
    public void settlesWalletCreditsAndAllocatesTheBalance() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Wallet wallet = new Wallet(FastMoney.ofMinor(usd, 8050));

        wallet.credit(FastMoney.ofMinor(usd, 1950));
        wallet.debit(FastMoney.ofMinor(usd, 1225));

        FastMoney balance = wallet.balance();
        assertThat(balance).isEqualTo(FastMoney.of(new BigDecimal("87.75"), usd));
        assertThat(balance.getCurrency()).isEqualTo(usd);
        assertThat(balance.getNumber().numberValue(BigDecimal.class))
                .isEqualByComparingTo("87.75");

        FastMoney[] wholeDollarAllocation = balance.divideAndRemainder(3);
        assertThat(wholeDollarAllocation[0])
                .isEqualTo(FastMoney.of(new BigDecimal("29.00"), usd));
        assertThat(wholeDollarAllocation[1])
                .isEqualTo(FastMoney.of(new BigDecimal("0.75"), usd));
        assertThat(wholeDollarAllocation[0].multiply(3).add(wholeDollarAllocation[1]))
                .isEqualTo(balance);
    }

    private static final class Wallet {
        private FastMoney balance;

        private Wallet(FastMoney openingBalance) {
            this.balance = openingBalance;
        }

        private void credit(FastMoney amount) {
            balance = balance.add(amount);
        }

        private void debit(FastMoney amount) {
            balance = balance.subtract(amount);
        }

        private FastMoney balance() {
            return balance;
        }
    }
}
