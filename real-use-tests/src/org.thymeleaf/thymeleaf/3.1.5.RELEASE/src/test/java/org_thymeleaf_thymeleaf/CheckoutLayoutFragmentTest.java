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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's reusable template fragment and layout composition subsystem for a checkout page.
public class CheckoutLayoutFragmentTest {
    @Test
    public void rendersSelectedCheckoutFragments() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context();
        context.setVariable("pageTitle", "Checkout");
        context.setVariable("customer", "Nora");
        context.setVariable("orderNumber", "A-1042");
        context.setVariable("total", "$84.00");

        String header = templateEngine.process(CHECKOUT_TEMPLATE, Set.of("header"), context);
        String summary = templateEngine.process(CHECKOUT_TEMPLATE, Set.of("summary"), context);
        String normalizedHeader = header.replaceAll("\\s+", " ");
        String normalizedSummary = summary.replaceAll("\\s+", " ");

        assertTrue(normalizedHeader.contains("<header> <h1>Checkout</h1> <p>Welcome, Nora</p> </header>"));
        assertTrue(normalizedSummary.contains("<section class=\"order-summary\"> <h2>Order A-1042</h2>"
                + " <strong>$84.00</strong> </section>"));
        assertFalse(header.contains("th:fragment"));
        assertFalse(summary.contains("th:fragment"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private static final String CHECKOUT_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <body>
            <div th:fragment="header">
              <header>
                <h1 th:text="${pageTitle}">Title</h1>
                <p th:text="|Welcome, ${customer}|">Welcome</p>
              </header>
            </div>
            <section th:fragment="summary" class="order-summary">
              <h2 th:text="|Order ${orderNumber}|">Order</h2>
              <strong th:text="${total}">$0.00</strong>
            </section>
            </body>
            </html>
            """;
}
