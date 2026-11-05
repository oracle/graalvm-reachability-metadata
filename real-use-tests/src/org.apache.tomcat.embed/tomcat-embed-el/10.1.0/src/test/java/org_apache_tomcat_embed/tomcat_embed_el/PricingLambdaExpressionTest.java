/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ExpressionFactory;
import jakarta.el.LambdaExpression;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Evaluates standalone Jakarta Expression Language lambda expressions for configurable pricing.
 * An order service can compile one deployment-provided pricing rule and apply it to each order
 * without embedding the rule in its request-handling code.
 */
public class PricingLambdaExpressionTest {

    @Test
    @Timeout(60)
    public void appliesCompiledPricingRuleToOrders() {
        ExpressionFactory factory = ExpressionFactory.newInstance();
        StandardELContext context = new StandardELContext(factory);
        ValueExpression ruleBody = factory.createValueExpression(
                context, "#{subtotal * (100 + taxPercent) / 100 + deliveryFee}", Object.class);
        LambdaExpression pricingRule = new LambdaExpression(
                List.of("subtotal", "taxPercent", "deliveryFee"), ruleBody);
        pricingRule.setELContext(context);

        Number priorityOrderTotal = (Number) pricingRule.invoke(context, 125, 20, 18);
        Number standardOrderTotal = (Number) pricingRule.invoke(context, 80, 10, 12);

        assertEquals(168, priorityOrderTotal.intValue());
        assertEquals(100, standardOrderTotal.intValue());
    }
}
