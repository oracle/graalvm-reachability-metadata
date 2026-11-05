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
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's classloader template resolution subsystem for an account summary.
public class ClassLoaderTemplateResolutionTest {
    @Test
    public void rendersTemplateLoadedFromApplicationResources() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("account", new Account("Ada", "ada@example.test"));
        context.setVariable("openOrders", 3);

        String rendered = templateEngine.process("account/summary", context);

        assertTrue(rendered.contains("<h1>Account for Ada</h1>"));
        assertTrue(rendered.contains("<a href=\"account/ada@example.test/orders\">"));
        assertTrue(rendered.contains("Open orders (<span>3</span>)"));
        assertFalse(rendered.contains("th:"));
    }

    private record Account(String displayName, String email) {
    }
}
