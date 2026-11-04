/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.runtime.directive.Directive;
import org.apache.velocity.runtime.directive.DirectiveConstants;
import org.apache.velocity.runtime.parser.node.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's custom directive subsystem for formatting invoice totals.
public class CustomInvoiceDirectiveTest {
    @Test
    @Timeout(60)
    public void rendersInvoiceUsingApplicationDirective() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty("userdirective", MoneyDirective.class.getName());
        engine.init();

        Invoice invoice = new Invoice("INV-2048", new BigDecimal("125.50"), "USD");
        VelocityContext context = new VelocityContext();
        context.put("invoice", invoice);

        StringWriter output = new StringWriter();
        boolean rendered = engine.evaluate(
                context,
                output,
                "invoice-view",
                "Invoice $invoice.number\nTotal: #money($invoice.total $invoice.currency)");

        assertTrue(rendered);
        assertEquals("Invoice INV-2048\nTotal: USD 125.50", output.toString());
    }

    public static final class MoneyDirective extends Directive {
        @Override
        public String getName() {
            return "money";
        }

        @Override
        public int getType() {
            return DirectiveConstants.LINE;
        }

        @Override
        public boolean render(InternalContextAdapter context, Writer writer, Node node) throws IOException {
            BigDecimal amount = (BigDecimal) node.jjtGetChild(0).value(context);
            String currency = (String) node.jjtGetChild(1).value(context);
            writer.write(currency + " " + amount.toPlainString());
            return true;
        }
    }

    public static final class Invoice {
        private final String number;
        private final BigDecimal total;
        private final String currency;

        public Invoice(String number, BigDecimal total, String currency) {
            this.number = number;
            this.total = total;
            this.currency = currency;
        }

        public String getNumber() {
            return number;
        }

        public BigDecimal getTotal() {
            return total;
        }

        public String getCurrency() {
            return currency;
        }
    }
}
