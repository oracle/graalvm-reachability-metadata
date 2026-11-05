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
import org.thymeleaf.templateparser.markup.decoupled.StandardDecoupledTemplateLogicResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's decoupled template logic subsystem for a customer profile page.
public class DecoupledTemplateLogicTest {
    @Test
    public void rendersProfileUsingLogicStoredOutsideMarkupTemplate() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        resolver.setUseDecoupledLogic(true);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setDecoupledTemplateLogicResolver(new StandardDecoupledTemplateLogicResolver());

        Context context = new Context();
        context.setVariable("customer", new Customer("Mina Patel", "mina@example.test"));
        context.setVariable("membership", "Gold");

        String rendered = templateEngine.process("profile", context);

        assertTrue(rendered.contains("<h1>Mina Patel</h1>"));
        assertTrue(rendered.contains("<p class=\"membership\">Gold member</p>"));
        assertTrue(rendered.contains("mailto:mina@example.test"));
        assertFalse(rendered.contains("th:"));
    }

    private record Customer(String name, String email) {
    }
}
