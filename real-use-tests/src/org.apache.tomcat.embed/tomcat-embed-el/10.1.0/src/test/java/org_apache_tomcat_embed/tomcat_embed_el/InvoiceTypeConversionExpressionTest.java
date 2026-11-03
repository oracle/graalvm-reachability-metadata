/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELContext;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.TypeConverter;
import jakarta.el.ValueExpression;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Converts submitted invoice values with Jakarta Expression Language's type-conversion subsystem.
 * A checkout application can bind localized form strings to its typed invoice model while keeping
 * parsing policy in the expression context.
 */
public class InvoiceTypeConversionExpressionTest {

    @Test
    @Timeout(60)
    public void bindsSubmittedAmountThroughApplicationTypeConverter() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        context.addELResolver(new InvoiceTypeConverter());
        Invoice invoice = new Invoice();
        context.getVariableMapper().setVariable(
                "invoice", factory.createValueExpression(invoice, Invoice.class));

        ValueExpression amount = factory.createValueExpression(
                context, "#{invoice.amount}", Object.class);
        amount.setValue(context, "EUR 125.50");
        ValueExpression taxRate = factory.createValueExpression(
                context, "#{invoice.taxRate}", Object.class);
        taxRate.setValue(context, "0.20");

        String currency = factory.createValueExpression(
                context, "#{invoice.amount.currency}", String.class).getValue(context);
        BigDecimal gross = factory.createValueExpression(
                context, "#{invoice.amount.value * (1 + invoice.taxRate)}", BigDecimal.class)
                .getValue(context);

        assertEquals("EUR", currency);
        assertEquals(new BigDecimal("150.6000"), gross);
        assertEquals(new Money("EUR", new BigDecimal("125.50")), invoice.getAmount());
        assertEquals(new BigDecimal("0.20"), invoice.getTaxRate());
    }

    /** Converts the checkout form's localized strings into the invoice model's value types. */
    public static final class InvoiceTypeConverter extends TypeConverter {
        @Override
        public <T> T convertToType(ELContext context, Object value, Class<T> targetType) {
            if (value instanceof String text && targetType == Money.class) {
                String[] parts = text.trim().split(" ", 2);
                context.setPropertyResolved(true);
                return targetType.cast(new Money(
                        parts[0].toUpperCase(Locale.ROOT), new BigDecimal(parts[1])));
            }
            if (value instanceof String text && targetType == BigDecimal.class) {
                context.setPropertyResolved(true);
                return targetType.cast(new BigDecimal(text));
            }
            return null;
        }
    }

    /** Typed invoice model populated from the checkout application's submitted fields. */
    public static final class Invoice {
        private Money amount;
        private BigDecimal taxRate;

        public Money getAmount() {
            return amount;
        }

        public void setAmount(Money amount) {
            this.amount = amount;
        }

        public BigDecimal getTaxRate() {
            return taxRate;
        }

        public void setTaxRate(BigDecimal taxRate) {
            this.taxRate = taxRate;
        }
    }

    /** Currency amount used by the invoice model and rendered by the checkout application. */
    public static final class Money {
        private final String currency;
        private final BigDecimal value;

        public Money(String currency, BigDecimal value) {
            this.currency = currency;
            this.value = value;
        }

        public String getCurrency() {
            return currency;
        }

        public BigDecimal getValue() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Money money)) {
                return false;
            }
            return currency.equals(money.currency) && value.compareTo(money.value) == 0;
        }

        @Override
        public int hashCode() {
            return 31 * currency.hashCode() + value.stripTrailingZeros().hashCode();
        }
    }
}
