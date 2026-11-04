/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's reusable macro and collection-rendering subsystem for invoice summaries.
public class InvoiceSummaryTemplateTest {
    @Test
    @Timeout(60)
    public void rendersInvoiceLinesThroughMacroAndConditionals() {
        VelocityEngine engine = new VelocityEngine();
        engine.init();

        VelocityContext context = new VelocityContext();
        context.put("customerName", "Ari");
        context.put("lines", List.of(
                new LineItem("NOTEBOOK", "Notebook", 2, 12.50),
                new LineItem("PEN", "Pen", 1, 2.00)));

        String template = "#macro(line $item)$item.sku: $item.quantity x $item.name\n"
                + "#end\n"
                + "Invoice for $customerName\n"
                + "#foreach($line in $lines)#line($line)#end"
                + "#if($lines.size() > 1)Multiple items#end";
        StringWriter invoice = new StringWriter();

        boolean rendered = engine.evaluate(context, invoice, "invoice-summary", template);

        assertTrue(rendered);
        assertEquals("Invoice for Ari\nNOTEBOOK: 2 x Notebook\nPEN: 1 x Pen\nMultiple items", invoice.toString());
    }

    public static final class LineItem {
        private final String sku;
        private final String name;
        private final int quantity;
        private final double unitPrice;

        public LineItem(String sku, String name, int quantity, double unitPrice) {
            this.sku = sku;
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }
    }
}
