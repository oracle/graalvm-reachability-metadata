/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ExpressionFactory;
import jakarta.el.MethodExpression;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compiles and invokes reusable notification expressions with Jakarta Expression Language's
 * standard expression factory API. A fulfillment application prepares expressions once, then
 * evaluates a notification and delivery label for each order.
 */
public class NotificationTemplateExpressionTest {

    @Test
    @Timeout(60)
    public void rendersNotificationFromBeanAndMapVariables() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        Customer customer = new Customer("Mina", true);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("number", "ORD-2048");
        order.put("total", 149);
        context.getVariableMapper().setVariable(
                "customer", factory.createValueExpression(customer, Customer.class));
        context.getVariableMapper().setVariable(
                "order", factory.createValueExpression(order, Map.class));

        ValueExpression subject = factory.createValueExpression(
                context, "#{order['subject']}", String.class);
        subject.setValue(context, "Dispatch ready");
        ValueExpression notification = factory.createValueExpression(
                context,
                "#{customer.name.concat(': ').concat(order['subject']).concat(' for ').concat(order['number'])}",
                String.class);
        MethodExpression deliveryLabel = factory.createMethodExpression(
                context,
                "#{customer.getDeliveryLabel()}",
                String.class,
                new Class<?>[0]);

        String renderedNotification = notification.getValue(context);
        String renderedDeliveryLabel = (String) deliveryLabel.invoke(context, new Object[0]);

        assertEquals("Dispatch ready", subject.getValue(context));
        assertEquals("Mina: Dispatch ready for ORD-2048", renderedNotification);
        assertEquals("priority delivery", renderedDeliveryLabel);
    }

    /** Customer profile used by the notification template. */
    public static final class Customer {
        private final String name;
        private final boolean priority;

        public Customer(String name, boolean priority) {
            this.name = name;
            this.priority = priority;
        }

        public String getName() {
            return name;
        }

        public String getDeliveryLabel() {
            return priority ? "priority delivery" : "standard delivery";
        }
    }
}
