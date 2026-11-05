/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MoneyProducer;
import org.javamoney.moneta.function.MonetaryAmountProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's monetary amount producer subsystem for importing ledger rows.
 */
public class LedgerAmountProducerTest {
    @Test
    @Timeout(60)
    public void convertsImportedNumbersIntoLedgerAmounts() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Ledger ledger = new Ledger(new MoneyProducer(), usd);

        ledger.importRow(new BigDecimal("18.40"));
        ledger.importRow(new BigDecimal("27.65"));
        ledger.importRow(new BigDecimal("92.10"));

        assertThat(ledger.amounts()).containsExactly(
                Money.of(new BigDecimal("18.40"), usd),
                Money.of(new BigDecimal("27.65"), usd),
                Money.of(new BigDecimal("92.10"), usd));
        assertThat(ledger.total()).isEqualTo(Money.of(new BigDecimal("138.15"), usd));
    }

    private static final class Ledger {
        private final MonetaryAmountProducer producer;
        private final CurrencyUnit currency;
        private final List<MonetaryAmount> amounts = new ArrayList<>();

        private Ledger(MonetaryAmountProducer producer, CurrencyUnit currency) {
            this.producer = producer;
            this.currency = currency;
        }

        private void importRow(Number amount) {
            amounts.add(producer.create(currency, amount));
        }

        private List<MonetaryAmount> amounts() {
            return List.copyOf(amounts);
        }

        private Money total() {
            Money total = Money.zero(currency);
            for (MonetaryAmount amount : amounts) {
                total = Money.from(total.add(amount));
            }
            return total;
        }
    }
}
