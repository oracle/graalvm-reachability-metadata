/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELProcessor;
import java.util.ListResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resolves checkout messages with Jakarta Expression Language's resource bundle resolver
 * subsystem. A checkout application can keep customer-facing labels in a bundle while its
 * configured expressions select messages from the current order state.
 */
public class CheckoutMessageBundleExpressionTest {

    @Test
    @Timeout(60)
    public void rendersLocalizedCheckoutMessagesFromOrderState() {
        ELProcessor expressions = new ELProcessor();
        expressions.defineBean("messages", new CheckoutMessages());
        expressions.defineBean("order", new Order("READY", "Mina", 149));

        String statusLabel = expressions.getValue("messages[order.status]", String.class);
        String confirmation = expressions.getValue(
                "messages['confirmation'].concat(': ').concat(order.customer)", String.class);
        String missingMessage = expressions.getValue("messages['missing.label']", String.class);

        assertEquals("Ready for dispatch", statusLabel);
        assertEquals("Order confirmed: Mina", confirmation);
        assertEquals("???missing.label???", missingMessage);
    }

    /** Checkout messages supplied by the application's localization layer. */
    public static final class CheckoutMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"READY", "Ready for dispatch"},
                    {"confirmation", "Order confirmed"}
            };
        }
    }

    /** Order fields selected by the configured checkout expressions. */
    public static final class Order {
        private final String status;
        private final String customer;
        private final int total;

        public Order(String status, String customer, int total) {
            this.status = status;
            this.customer = customer;
            this.total = total;
        }

        public String getStatus() {
            return status;
        }

        public String getCustomer() {
            return customer;
        }

        public int getTotal() {
            return total;
        }
    }
}
