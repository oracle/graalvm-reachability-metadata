/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import ognl.TypeConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Member;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's custom type-converter subsystem for a billing form.
 *
 * A billing application receives a localized money field as text and uses OGNL to bind that
 * submitted value to the typed invoice model used by the payment workflow.
 */
public class BillingFormTypeConverterTest {
    @Test
    @Timeout(60)
    void bindsSubmittedMoneyTextThroughApplicationTypeConverter() throws OgnlException {
        Invoice invoice = new Invoice();
        BillingTypeConverter converter = new BillingTypeConverter();
        Map context = Ognl.createDefaultContext(invoice, null, converter);

        Ognl.setValue("total", context, invoice, "EUR 149.90");
        Object displayedTotal = Ognl.getValue("total.display", context, invoice);

        assertThat(invoice.getTotal()).isEqualTo(new Money("EUR", new BigDecimal("149.90")));
        assertThat(displayedTotal).isEqualTo("EUR 149.90");
        assertThat(converter.convertedProperties()).containsExactly("total");
    }

    public static final class BillingTypeConverter implements TypeConverter {
        private final List<String> convertedProperties = new ArrayList<>();

        @Override
        public Object convertValue(
                Map context,
                Object target,
                Member member,
                String propertyName,
                Object value,
                Class toType) {
            if (toType == Money.class && value instanceof String) {
                convertedProperties.add(propertyName);
                return Money.parse((String) value);
            }
            return value;
        }

        public List<String> convertedProperties() {
            return convertedProperties;
        }
    }

    public static final class Invoice {
        private Money total;

        public Money getTotal() {
            return total;
        }

        public void setTotal(Money total) {
            this.total = total;
        }
    }

    public static final class Money {
        private final String currency;
        private final BigDecimal amount;

        private Money(String currency, BigDecimal amount) {
            this.currency = currency;
            this.amount = amount;
        }

        static Money parse(String text) {
            String[] parts = text.split(" ", 2);
            return new Money(parts[0], new BigDecimal(parts[1]));
        }

        public String getDisplay() {
            return currency + " " + amount.setScale(2);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Money
                    && currency.equals(((Money) other).currency)
                    && amount.compareTo(((Money) other).amount) == 0;
        }

        @Override
        public int hashCode() {
            return 31 * currency.hashCode() + amount.stripTrailingZeros().hashCode();
        }
    }
}
