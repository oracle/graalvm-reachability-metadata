/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryOperators;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's per-mille operator subsystem for a marketplace payment fee.
 */
public class MarketplaceFeeTest {
    @Test
    @Timeout(60)
    public void calculatesMarketplaceFeeFromPerMilleRate() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Money payment = Money.of(new BigDecimal("845.00"), usd);
        MarketplaceSettlement settlement = new MarketplaceSettlement(new BigDecimal("12.5"));

        settlement.accept(payment);

        assertThat(settlement.fee()).isEqualTo(Money.of(new BigDecimal("10.5625"), usd));
        assertThat(settlement.payout()).isEqualTo(Money.of(new BigDecimal("834.4375"), usd));
        assertThat(settlement.fee().getCurrency()).isEqualTo(usd);
    }

    private static final class MarketplaceSettlement {
        private final BigDecimal feePerMille;
        private Money payment;

        private MarketplaceSettlement(BigDecimal feePerMille) {
            this.feePerMille = feePerMille;
        }

        private void accept(Money payment) {
            this.payment = payment;
        }

        private Money fee() {
            return Money.from(payment.with(MonetaryOperators.permil(feePerMille)));
        }

        private Money payout() {
            return Money.from(payment.subtract(fee()));
        }
    }
}
