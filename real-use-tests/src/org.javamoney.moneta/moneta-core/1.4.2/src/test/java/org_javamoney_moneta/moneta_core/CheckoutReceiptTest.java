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
import javax.money.MonetaryAmount;
import javax.money.MonetaryRounding;
import javax.money.format.MonetaryAmountFormat;
import javax.money.format.MonetaryFormats;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises Moneta's shopping-cart monetary arithmetic and receipt-formatting subsystem for a checkout.
 */
public class CheckoutReceiptTest {
    @Test
    @Timeout(60)
    public void calculatesDiscountTaxAndReceiptTotal() {
        CurrencyUnit usd = Monetary.getCurrency("USD");
        List<LineItem> items = List.of(
                new LineItem("coffee", Money.of(new BigDecimal("12.50"), usd), 2),
                new LineItem("tea", Money.of(new BigDecimal("7.35"), usd), 3),
                new LineItem("mug", Money.of(new BigDecimal("4.99"), usd), 1));

        Money subtotal = items.stream()
                .map(LineItem::total)
                .reduce(Money.zero(usd), (running, item) -> Money.from(running.add(item)));
        MonetaryRounding rounding = Monetary.getRounding(usd);
        Money discount = Money.from(subtotal.multiply(new BigDecimal("0.10")).with(rounding));
        Money taxable = Money.from(subtotal.subtract(discount));
        Money tax = Money.from(taxable.multiply(new BigDecimal("0.0825")).with(rounding));
        Money total = Money.from(taxable.add(tax));

        assertThat(subtotal).isEqualTo(Money.of(new BigDecimal("52.04"), usd));
        assertThat(discount).isEqualTo(Money.of(new BigDecimal("5.20"), usd));
        assertThat(tax).isEqualTo(Money.of(new BigDecimal("3.86"), usd));
        assertThat(total).isEqualTo(Money.of(new BigDecimal("50.70"), usd));
        assertThat(total.query(MonetaryQueries.convertMinorPart())).isEqualTo(5070L);

        MonetaryAmountFormat receiptFormat = MonetaryFormats.getAmountFormat(Locale.US);
        String printedTotal = receiptFormat.format(total);
        MonetaryAmount parsedTotal = receiptFormat.parse(printedTotal);

        assertThat(printedTotal).isEqualTo("USD50.70");
        assertThat(parsedTotal).isEqualTo(total);
    }

    private static final class LineItem {
        private final String sku;
        private final Money unitPrice;
        private final int quantity;

        private LineItem(String sku, Money unitPrice, int quantity) {
            this.sku = sku;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        private Money total() {
            assertThat(sku).isNotBlank();
            return unitPrice.multiply(quantity);
        }
    }
}
