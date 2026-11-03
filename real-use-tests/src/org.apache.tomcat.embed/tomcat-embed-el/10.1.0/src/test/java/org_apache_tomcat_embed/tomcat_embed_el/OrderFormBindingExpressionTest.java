/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Binds submitted order fields through Jakarta Expression Language's writable bean resolver
 * subsystem. A checkout application can apply validated form values to its order model before
 * evaluating the customer-facing total and confirmation text.
 */
public class OrderFormBindingExpressionTest {

    @Test
    @Timeout(60)
    public void appliesSubmittedFieldsToOrderBean() {
        OrderForm order = new OrderForm("ORD-11871", 2, 35);
        ELProcessor expressions = new ELProcessor();
        expressions.defineBean("order", order);

        expressions.setValue("order.quantity", 4);
        expressions.setValue("order.unitPrice", 40);

        Number subtotal = expressions.eval("order.quantity * order.unitPrice");
        String confirmation = expressions.eval(
                "order.id.concat(' / ').concat(order.quantity).concat(' items for ')"
                        + ".concat(order.quantity * order.unitPrice)");

        assertEquals(4, order.getQuantity());
        assertEquals(40, order.getUnitPrice());
        assertEquals(160, subtotal.intValue());
        assertEquals("ORD-11871 / 4 items for 160", confirmation);
    }

    /** Mutable checkout model populated from the application's submitted form. */
    public static final class OrderForm {
        private final String id;
        private int quantity;
        private int unitPrice;

        public OrderForm(String id, int quantity, int unitPrice) {
            this.id = id;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getId() {
            return id;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public int getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(int unitPrice) {
            this.unitPrice = unitPrice;
        }
    }
}
