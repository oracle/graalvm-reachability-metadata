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
import javax.money.MonetaryAmountFactory;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's monetary amount factory subsystem for an invoice assembled from line items.
 */
public class InvoiceAmountFactoryTest {
    @Test
    @Timeout(60)
    public void buildsInvoiceAmountsFromApplicationLineItems() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        Invoice invoice = new Invoice(usd, List.of(
                new LineItem("annual subscription", new BigDecimal("129.99"), 1),
                new LineItem("additional seats", new BigDecimal("24.50"), 3),
                new LineItem("support package", new BigDecimal("48.75"), 2)));

        Money total = invoice.total();

        assertThat(total).isEqualTo(Money.of(new BigDecimal("300.99"), usd));
        assertThat(total.getCurrency()).isEqualTo(usd);
        assertThat(total.getNumber().numberValue(BigDecimal.class))
                .isEqualByComparingTo("300.99");
    }

    private static final class Invoice {
        private final CurrencyUnit currency;
        private final List<LineItem> items;

        private Invoice(CurrencyUnit currency, List<LineItem> items) {
            this.currency = currency;
            this.items = items;
        }

        private Money total() {
            MonetaryAmountFactory<Money> factory = Monetary.getAmountFactory(Money.class);
            Money total = Money.zero(currency);
            for (LineItem item : items) {
                Money unitPrice = factory.setCurrency(currency)
                        .setNumber(item.unitPrice)
                        .create();
                total = Money.from(total.add(unitPrice.multiply(item.quantity)));
            }
            return total;
        }
    }

    private static final class LineItem {
        private final String description;
        private final BigDecimal unitPrice;
        private final int quantity;

        private LineItem(String description, BigDecimal unitPrice, int quantity) {
            this.description = description;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }
    }
}
