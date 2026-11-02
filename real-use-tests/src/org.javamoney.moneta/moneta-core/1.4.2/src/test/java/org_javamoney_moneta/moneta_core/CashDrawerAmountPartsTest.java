/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javamoney_moneta.moneta_core;

import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's major- and minor-part query subsystem for a cash drawer receipt.
 */
public class CashDrawerAmountPartsTest {
    @Test
    @Timeout(60)
    public void separatesReceiptAmountIntoCashParts() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Money receiptTotal = Money.of(new BigDecimal("123.45"), usd);
        CashDrawer drawer = new CashDrawer();

        drawer.record(receiptTotal);

        assertThat(drawer.dollars()).isEqualTo(123L);
        assertThat(drawer.cents()).isEqualTo(45L);
        assertThat(drawer.totalInCents()).isEqualTo(12_345L);
    }

    private static final class CashDrawer {
        private Money receiptTotal;

        private void record(Money total) {
            receiptTotal = total;
        }

        private long dollars() {
            return receiptTotal.query(MonetaryQueries.extractMajorPart());
        }

        private long cents() {
            return receiptTotal.query(MonetaryQueries.extractMinorPart());
        }

        private long totalInCents() {
            return receiptTotal.query(MonetaryQueries.convertMinorPart());
        }
    }
}
