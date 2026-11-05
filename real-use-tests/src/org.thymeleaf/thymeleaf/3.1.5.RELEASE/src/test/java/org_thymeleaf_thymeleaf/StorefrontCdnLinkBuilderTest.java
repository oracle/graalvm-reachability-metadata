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
import org.thymeleaf.linkbuilder.AbstractLinkBuilder;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's custom link-building subsystem for a storefront CDN.
public class StorefrontCdnLinkBuilderTest {
    @Test
    public void rendersVersionedAssetLinksThroughApplicationCdnBuilder() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context();
        context.setVariable("stylesheet", "storefront.css");
        context.setVariable("assetVersion", "2025.06");

        String rendered = templateEngine.process(STOREFRONT_TEMPLATE, context);

        assertTrue(rendered.contains(
                "href=\"https://cdn.example.test/assets/storefront.css?v=2025.06\""));
        assertTrue(rendered.contains("<h1>Storefront</h1>"));
        assertFalse(rendered.contains("cdn:/"));
        assertFalse(rendered.contains("th:"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.addLinkBuilder(new CdnLinkBuilder());
        return templateEngine;
    }

    private static final class CdnLinkBuilder extends AbstractLinkBuilder {
        private CdnLinkBuilder() {
            setName("storefront-cdn");
            setOrder(1);
        }

        @Override
        public String buildLink(
                org.thymeleaf.context.IExpressionContext context,
                String base,
                Map<String, Object> parameters) {
            if (!base.startsWith("cdn:")) {
                return null;
            }
            String assetPath = base.substring("cdn:".length());
            String assetName = String.valueOf(parameters.get("file"));
            String version = String.valueOf(parameters.get("v"));
            return "https://cdn.example.test" + assetPath.replace("{file}", assetName)
                    + "?v=" + version;
        }
    }

    private static final String STOREFRONT_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <head>
              <link rel="stylesheet" th:href="@{cdn:/assets/{file}(file=${stylesheet},v=${assetVersion})}">
            </head>
            <body>
            <h1>Storefront</h1>
            </body>
            </html>
            """;
}
