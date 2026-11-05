/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf_thymeleaf;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's Spring Framework integration subsystem for a checkout confirmation.
public class SpringCheckoutConfirmationTest {
    @Test
    public void rendersConfirmationWithSpringMessagesAndApplicationValues() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("checkout.title", Locale.US, "Order {0} confirmed");
        messageSource.addMessage("checkout.nextStep", Locale.US, "We will email {0} when it ships.");

        SpringTemplateEngine templateEngine = newTemplateEngine(messageSource);
        Context context = new Context(Locale.US);
        context.setVariable("orderNumber", "A-1042");
        context.setVariable("email", "customer@example.test");
        context.setVariable("total", "$84.00");

        String rendered = templateEngine.process(CONFIRMATION_TEMPLATE, context);

        assertTrue(rendered.contains("<h1>Order A-1042 confirmed</h1>"));
        assertTrue(rendered.contains("<p class=\"total\">Total: $84.00</p>"));
        assertTrue(rendered.contains("We will email customer@example.test when it ships."));
        assertFalse(rendered.contains("#{"));
        assertFalse(rendered.contains("th:"));
    }

    private static SpringTemplateEngine newTemplateEngine(StaticMessageSource messageSource) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setMessageSource(messageSource);
        return templateEngine;
    }

    private static final String CONFIRMATION_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <body>
            <h1 th:text="#{checkout.title(${orderNumber})}">Checkout</h1>
            <p class="total" th:text="|Total: ${total}|">Total</p>
            <p th:text="#{checkout.nextStep(${email})}">Next step</p>
            </body>
            </html>
            """;
}
