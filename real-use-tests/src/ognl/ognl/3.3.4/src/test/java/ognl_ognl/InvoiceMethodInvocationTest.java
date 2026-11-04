/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl_ognl;

import ognl.Ognl;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises OGNL's method-invocation subsystem for rendering an invoice from a quote.
 *
 * An invoice renderer invokes application methods, a JDK static method, and a constructor from
 * expressions while keeping the quote model independent of the view.
 */
public class InvoiceMethodInvocationTest {
    @Test
    @Timeout(60)
    void invokesQuoteMethodsAndJdkHelpersForInvoiceRendering() throws OgnlException {
        Quote quote = Quote.sample();
        Map context = Ognl.createDefaultContext(quote);

        Object customerLabel = Ognl.getValue("customerLabel()", context, quote);
        Object total = Ognl.getValue("totalIn('EUR')", context, quote);
        Object invoiceLine = Ognl.getValue(
                "@java.lang.String@format('%s | %s', reference, customerLabel())",
                context,
                quote);
        Object deposit = Ognl.getValue("new java.math.BigDecimal('25.00')", context, quote);

        assertThat(customerLabel).isEqualTo("Northwind / GOLD");
        assertThat(total).isEqualTo(new BigDecimal("149.90"));
        assertThat(invoiceLine).isEqualTo("Q-1042 | Northwind / GOLD");
        assertThat(deposit).isEqualTo(new BigDecimal("25.00"));
    }

    public static final class Quote {
        private final String reference;
        private final String customerName;
        private final String tier;
        private final BigDecimal subtotal;
        private final BigDecimal shipping;

        private Quote(
                String reference,
                String customerName,
                String tier,
                BigDecimal subtotal,
                BigDecimal shipping) {
            this.reference = reference;
            this.customerName = customerName;
            this.tier = tier;
            this.subtotal = subtotal;
            this.shipping = shipping;
        }

        static Quote sample() {
            return new Quote(
                    "Q-1042",
                    "Northwind",
                    "GOLD",
                    new BigDecimal("129.90"),
                    new BigDecimal("20.00"));
        }

        public String getReference() {
            return reference;
        }

        public String customerLabel() {
            return customerName + " / " + tier;
        }

        public BigDecimal totalIn(String currency) {
            if (!"EUR".equals(currency)) {
                throw new IllegalArgumentException("Unsupported currency: " + currency);
            }
            return subtotal.add(shipping);
        }
    }
}
