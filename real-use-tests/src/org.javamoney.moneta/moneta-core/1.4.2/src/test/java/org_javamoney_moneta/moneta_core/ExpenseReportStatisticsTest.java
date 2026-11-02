/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;
import org.javamoney.moneta.function.MonetarySummaryStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's monetary stream statistics subsystem for a multi-currency expense report.
 */
public class ExpenseReportStatisticsTest {
    @Test
    @Timeout(60)
    public void summarizesExpensesByCurrency() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        CurrencyUnit eur = Monetary.getCurrency("EUR");
        List<Expense> expenses = List.of(
                new Expense("train", Money.of(new BigDecimal("18.40"), usd)),
                new Expense("lunch", Money.of(new BigDecimal("27.65"), usd)),
                new Expense("hotel", Money.of(new BigDecimal("92.10"), usd)),
                new Expense("museum", Money.of(new BigDecimal("14.20"), eur)));
        List<MonetaryAmount> amounts = expenses.stream().map(Expense::amount).toList();

        Map<CurrencyUnit, List<MonetaryAmount>> byCurrency = amounts.stream()
                .collect(MonetaryFunctions.groupByCurrencyUnit());
        MonetarySummaryStatistics usdStatistics = byCurrency.get(usd).stream()
                .collect(MonetaryFunctions.summarizingMonetary(usd));
        MonetarySummaryStatistics eurStatistics = byCurrency.get(eur).stream()
                .collect(MonetaryFunctions.summarizingMonetary(eur));

        assertThat(usdStatistics.getCount()).isEqualTo(3);
        assertThat(usdStatistics.getMin()).isEqualTo(Money.of(new BigDecimal("18.40"), usd));
        assertThat(usdStatistics.getMax()).isEqualTo(Money.of(new BigDecimal("92.10"), usd));
        assertThat(usdStatistics.getSum()).isEqualTo(Money.of(new BigDecimal("138.15"), usd));
        assertThat(usdStatistics.getAverage()).isEqualTo(Money.of(new BigDecimal("46.05"), usd));
        assertThat(eurStatistics.getSum()).isEqualTo(Money.of(new BigDecimal("14.20"), eur));
        assertThat(amounts.stream().filter(MonetaryFunctions.isCurrency(usd)).count()).isEqualTo(3);
    }

    private static final class Expense {
        private final String description;
        private final MonetaryAmount amount;

        private Expense(String description, MonetaryAmount amount) {
            this.description = description;
            this.amount = amount;
        }

        private MonetaryAmount amount() {
            assertThat(description).isNotBlank();
            return amount;
        }
    }
}
