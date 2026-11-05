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
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's custom processor dialect subsystem for storefront status badges.
public class StorefrontBadgeDialectTest {
    @Test
    public void rendersApplicationStatusBadgesThroughCustomDialect() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context();
        context.setVariable("title", "Fulfillment queue");

        String rendered = templateEngine.process(STOREFRONT_TEMPLATE, context);

        assertTrue(rendered.contains("<h1>Fulfillment queue</h1>"));
        assertTrue(rendered.contains("class=\"status-badge status-badge-priority\">Priority</span>"));
        assertTrue(rendered.contains("class=\"status-badge status-badge-backorder\">Backorder</span>"));
        assertFalse(rendered.contains("store:badge"));
        assertFalse(rendered.contains("placeholder"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.addDialect(new StorefrontDialect());
        return templateEngine;
    }

    private static final class StorefrontDialect extends AbstractProcessorDialect {
        private StorefrontDialect() {
            super("Storefront", "store", 1000);
        }

        @Override
        public Set<IProcessor> getProcessors(String dialectPrefix) {
            return Set.of(new BadgeAttributeProcessor(dialectPrefix));
        }
    }

    private static final class BadgeAttributeProcessor extends AbstractAttributeTagProcessor {
        private BadgeAttributeProcessor(String dialectPrefix) {
            super(TemplateMode.HTML, dialectPrefix, null, false, "badge", true, 1000, true);
        }

        @Override
        protected void doProcess(
                ITemplateContext context,
                IProcessableElementTag tag,
                AttributeName attributeName,
                String attributeValue,
                IElementTagStructureHandler structureHandler) {
            String badgeClass = attributeValue.toLowerCase(Locale.ROOT).replace(' ', '-');
            structureHandler.setAttribute("class", "status-badge status-badge-" + badgeClass);
            structureHandler.setBody(attributeValue, false);
        }
    }

    private static final String STOREFRONT_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org" xmlns:store="http://example.test/storefront">
            <head><title th:text="${title}">Storefront</title></head>
            <body>
            <h1 th:text="${title}">Storefront</h1>
            <p><span store:badge="Priority">placeholder</span></p>
            <p><span store:badge="Backorder">placeholder</span></p>
            </body>
            </html>
            """;
}
