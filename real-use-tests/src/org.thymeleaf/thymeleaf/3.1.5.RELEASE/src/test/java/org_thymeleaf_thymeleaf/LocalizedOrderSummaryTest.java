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
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's internationalization and message resolution subsystem for an order summary.
public class LocalizedOrderSummaryTest {
    @Test
    public void rendersLocalizedMessagesWithApplicationValues() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context(Locale.GERMANY);
        context.setVariable("customer", "Mina");
        context.setVariable("itemCount", 3);

        String rendered = templateEngine.process(ORDER_TEMPLATE, context);

        assertTrue(rendered.contains("Willkommen, Mina!"));
        assertTrue(rendered.contains("3 Artikel"));
        assertTrue(rendered.contains("Ihre Bestellung"));
        assertFalse(rendered.contains("#{"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCacheable(false);

        StandardMessageResolver messageResolver = new StandardMessageResolver();
        Properties messages = new Properties();
        messages.setProperty("order.title", "Ihre Bestellung");
        messages.setProperty("welcome", "Willkommen, {0}!");
        messages.setProperty("item.count", "{0} Artikel");
        messageResolver.setDefaultMessages(messages);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        templateEngine.setMessageResolver(messageResolver);
        return templateEngine;
    }

    private static final String ORDER_TEMPLATE = """
            <!doctype html>
            <html>
            <body>
            <h1 th:text="#{order.title}">Order</h1>
            <p th:text="#{welcome(${customer})}">Welcome</p>
            <p th:text="#{item.count(${itemCount})}">Items</p>
            </body>
            </html>
            """;
}
