/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import jakarta.el.ValueReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Identifies form targets with Jakarta Expression Language's value-reference subsystem.
 * A checkout application can audit a configured field target before applying its validated
 * submission to the ordinary order model.
 */
public class ShippingAddressValueReferenceExpressionTest {

    @Test
    @Timeout(60)
    public void auditsAndUpdatesConfiguredShippingAddress() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        OrderForm order = new OrderForm("ORD-11871", "Old Town");
        context.getVariableMapper().setVariable(
                "order", factory.createValueExpression(order, OrderForm.class));
        ValueExpression address = factory.createValueExpression(
                context, "#{order.shippingAddress}", String.class);

        ValueReference target = address.getValueReference(context);
        address.setValue(context, "Harbor District");
        ValueExpression summary = factory.createValueExpression(
                context,
                "#{order.number.concat(' / ').concat(order.shippingAddress)}",
                String.class);

        assertSame(order, target.getBase());
        assertEquals("shippingAddress", target.getProperty());
        assertEquals("Harbor District", order.getShippingAddress());
        assertEquals("ORD-11871 / Harbor District", summary.getValue(context));
    }

    /** Mutable checkout model updated by the application's configured field binding. */
    public static final class OrderForm {
        private final String number;
        private String shippingAddress;

        public OrderForm(String number, String shippingAddress) {
            this.number = number;
            this.shippingAddress = shippingAddress;
        }

        public String getNumber() {
            return number;
        }

        public String getShippingAddress() {
            return shippingAddress;
        }

        public void setShippingAddress(String shippingAddress) {
            this.shippingAddress = shippingAddress;
        }
    }
}
