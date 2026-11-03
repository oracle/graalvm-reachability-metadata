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
 * Registers application-defined pricing functions with Jakarta Expression Language.
 * An order service can keep tax and delivery rules in reusable functions while allowing
 * a configurable expression to calculate the amount shown to a customer.
 */
public class OrderPricingFunctionExpressionTest {

    @Test
    @Timeout(60)
    public void appliesRegisteredPricingFunctionsToAnOrder() throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor expressions = new ELProcessor();
        expressions.defineFunction(
                "pricing", "taxedTotal", PricingFunctions.class.getName(), "taxedTotal");
        expressions.defineFunction(
                "pricing", "deliveryTotal", PricingFunctions.class.getName(), "deliveryTotal");
        expressions.defineBean("order", new Order(125, 20, 18));

        Number taxedTotal = expressions.eval(
                "pricing:taxedTotal(order.subtotal, order.taxPercent)");
        Number payableTotal = expressions.eval(
                "pricing:deliveryTotal(pricing:taxedTotal(order.subtotal, order.taxPercent),"
                        + " order.deliveryFee)");

        assertEquals(150, taxedTotal.intValue());
        assertEquals(168, payableTotal.intValue());
    }

    /** Pricing functions supplied by the order service to its configurable expressions. */
    public static final class PricingFunctions {

        private PricingFunctions() {
        }

        public static int taxedTotal(int subtotal, int taxPercent) {
            return subtotal + subtotal * taxPercent / 100;
        }

        public static int deliveryTotal(int subtotal, int deliveryFee) {
            return subtotal + deliveryFee;
        }
    }

    /** Order values exposed to the pricing expression by the application. */
    public static final class Order {
        private final int subtotal;
        private final int taxPercent;
        private final int deliveryFee;

        public Order(int subtotal, int taxPercent, int deliveryFee) {
            this.subtotal = subtotal;
            this.taxPercent = taxPercent;
            this.deliveryFee = deliveryFee;
        }

        public int getSubtotal() {
            return subtotal;
        }

        public int getTaxPercent() {
            return taxPercent;
        }

        public int getDeliveryFee() {
            return deliveryFee;
        }
    }
}
