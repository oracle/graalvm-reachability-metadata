/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf_thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's standard expression evaluation subsystem for an order total.
public class OrderPricingExpressionTest {
    @Test
    public void rendersFormattedOrderSummaryWithExpressionObjects() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context(Locale.US);
        context.setVariable("customer", "maria");
        context.setVariable("items", List.of("Notebook", "Pen"));
        context.setVariable("total", new BigDecimal("128.5"));

        String rendered = templateEngine.process(ORDER_TEMPLATE, context);

        assertTrue(rendered.contains("<h1>Maria</h1>"));
        assertTrue(rendered.contains("<p>2 items</p>"));
        assertTrue(rendered.contains("<strong>$128.50</strong>"));
        assertFalse(rendered.contains("th:"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private static final String ORDER_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <body>
            <h1 th:text="${#strings.capitalize(customer)}">Customer</h1>
            <p th:text="${#lists.size(items) + ' items'}">0 items</p>
            <strong th:text="${#numbers.formatCurrency(total)}">$0.00</strong>
            </body>
            </html>
            """;
}
