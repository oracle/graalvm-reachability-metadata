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

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Thymeleaf's plain-text template processing subsystem for a shipping notification.
public class ShippingNotificationTextTest {
    @Test
    public void rendersPlainTextNotificationForCustomer() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("recipient", "Nora");
        context.setVariable("trackingCode", "ZX-2048");
        context.setVariable("delivered", true);

        String rendered = templateEngine.process(NOTIFICATION_TEMPLATE, context);

        assertEquals("Hello, Nora!\nTracking: ZX-2048\nStatus: Delivered\n", rendered);
    }

    private static final String NOTIFICATION_TEMPLATE = """
            Hello, [(${recipient})]!
            Tracking: [(${trackingCode})]
            Status: [# th:if="${delivered}"]Delivered[/]
            """;
}
