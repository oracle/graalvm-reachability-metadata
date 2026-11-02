/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.CurrencyUnitBuilder;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's custom currency-unit subsystem for a rewards account that earns and redeems points.
 */
public class RewardsCurrencyAccountTest {
    @Test
    @Timeout(60)
    public void tracksRewardsPointsWithAnApplicationCurrency() {
        CurrencyUnit points = CurrencyUnitBuilder.of("PTS", "rewards")
                .setNumericCode(999)
                .setDefaultFractionDigits(0)
                .build();
        RewardsAccount account = new RewardsAccount(points);

        account.earn(Money.of(new BigDecimal("1250"), points));
        account.redeem(Money.of(new BigDecimal("375"), points));

        assertThat(points.getCurrencyCode()).isEqualTo("PTS");
        assertThat(points.getNumericCode()).isEqualTo(999);
        assertThat(points.getDefaultFractionDigits()).isZero();
        assertThat(points.getContext().getProviderName()).isEqualTo("rewards");
        assertThat(account.balance()).isEqualTo(Money.of(new BigDecimal("875"), points));
        assertThat(account.balance().getCurrency()).isEqualTo(points);
    }

    private static final class RewardsAccount {
        private final CurrencyUnit points;
        private Money balance;

        private RewardsAccount(CurrencyUnit points) {
            this.points = points;
            this.balance = Money.zero(points);
        }

        private void earn(Money amount) {
            balance = Money.from(balance.add(amount));
        }

        private void redeem(Money amount) {
            balance = Money.from(balance.subtract(amount));
        }

        private Money balance() {
            assertThat(balance.getCurrency()).isEqualTo(points);
            return balance;
        }
    }
}
