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

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's link expression subsystem for an order details page.
public class OrderLinkExpressionTest {
    @Test
    public void rendersParameterizedOrderLinksForApplicationNavigation() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("orderId", "A-1042");
        context.setVariable("view", "summary");

        String rendered = templateEngine.process(ORDER_TEMPLATE, context);

        assertTrue(rendered.contains("href=\"orders/A-1042?view=summary\""));
        assertTrue(rendered.contains("href=\"orders/A-1042/receipt\""));
        assertTrue(rendered.contains("View order A-1042"));
    }

    private static final String ORDER_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <body>
            <a th:href="@{orders/{id}(id=${orderId},view=${view})}"
               th:text="|View order ${orderId}|">View order</a>
            <a th:href="@{orders/{id}/receipt(id=${orderId})}">Receipt</a>
            </body>
            </html>
            """;
}
