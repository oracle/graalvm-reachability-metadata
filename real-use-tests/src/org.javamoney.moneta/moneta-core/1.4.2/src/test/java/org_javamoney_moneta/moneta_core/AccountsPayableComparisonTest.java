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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's monetary comparison subsystem for an accounts-payable approval queue.
 */
public class AccountsPayableComparisonTest {
    @Test
    @Timeout(60)
    public void prioritizesInvoicesAndChecksApprovalLimit() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        ApprovalDesk desk = new ApprovalDesk(Money.of(new BigDecimal("1000.00"), usd));
        Invoice hosting = new Invoice("hosting", Money.of(new BigDecimal("850.00"), usd));
        Invoice equipment = new Invoice("equipment", Money.of(new BigDecimal("1250.00"), usd));
        Invoice supplies = new Invoice("supplies", Money.of(new BigDecimal("325.00"), usd));

        desk.submit(hosting);
        desk.submit(equipment);
        desk.submit(supplies);

        assertThat(desk.prioritizedAmounts()).containsExactly(
                Money.of(new BigDecimal("1250.00"), usd),
                Money.of(new BigDecimal("850.00"), usd),
                Money.of(new BigDecimal("325.00"), usd));
        assertThat(desk.requiresApproval(equipment)).isTrue();
        assertThat(desk.requiresApproval(hosting)).isFalse();
    }

    private static final class ApprovalDesk {
        private final Money approvalLimit;
        private final List<Invoice> invoices = new ArrayList<>();

        private ApprovalDesk(Money approvalLimit) {
            this.approvalLimit = approvalLimit;
        }

        private void submit(Invoice invoice) {
            invoices.add(invoice);
        }

        private List<Money> prioritizedAmounts() {
            Comparator<Money> largestFirst = (left, right) -> right.compareTo(left);
            return invoices.stream()
                    .map(Invoice::amount)
                    .sorted(largestFirst)
                    .toList();
        }

        private boolean requiresApproval(Invoice invoice) {
            return invoice.amount().isGreaterThan(approvalLimit);
        }
    }

    private static final class Invoice {
        private final String description;
        private final Money amount;

        private Invoice(String description, Money amount) {
            this.description = description;
            this.amount = amount;
        }

        private Money amount() {
            assertThat(description).isNotBlank();
            return amount;
        }
    }
}
