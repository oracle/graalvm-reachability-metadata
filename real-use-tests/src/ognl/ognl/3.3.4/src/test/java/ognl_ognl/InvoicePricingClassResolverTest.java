/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.ClassResolver;
import ognl.DefaultClassResolver;
import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's class-resolution subsystem for an invoice pricing template.
 *
 * An invoicing application lets its saved templates use approved short names for domain value
 * types, so a template can construct the displayed total without exposing implementation names.
 */
public class InvoicePricingClassResolverTest {
    @Test
    @Timeout(60)
    void resolvesApprovedDomainTypeAliasInPricingTemplate() throws OgnlException {
        Invoice invoice = new Invoice(new BigDecimal("125.00"), new BigDecimal("10.00"));
        Map context = Ognl.createDefaultContext(invoice, new PricingClassResolver());

        Object displayedTotal = Ognl.getValue(
                "new Money(subtotal + tax)",
                context,
                invoice);

        assertThat(displayedTotal).isEqualTo(new Money(new BigDecimal("135.00")));
        assertThat(((Money) displayedTotal).display()).isEqualTo("EUR 135.00");
    }

    public static final class PricingClassResolver implements ClassResolver {
        private final DefaultClassResolver delegate = new DefaultClassResolver();

        @Override
        public Class<?> classForName(String className, Map context) throws ClassNotFoundException {
            if ("Money".equals(className)) {
                return Money.class;
            }
            return delegate.classForName(className, context);
        }
    }

    public static final class Invoice {
        private final BigDecimal subtotal;
        private final BigDecimal tax;

        private Invoice(BigDecimal subtotal, BigDecimal tax) {
            this.subtotal = subtotal;
            this.tax = tax;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public BigDecimal getTax() {
            return tax;
        }
    }

    public static final class Money {
        private final BigDecimal amount;

        public Money(BigDecimal amount) {
            this.amount = amount;
        }

        public String display() {
            return "EUR " + amount.setScale(2);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Money
                    && amount.compareTo(((Money) other).amount) == 0;
        }

        @Override
        public int hashCode() {
            return amount.stripTrailingZeros().hashCode();
        }
    }
}
