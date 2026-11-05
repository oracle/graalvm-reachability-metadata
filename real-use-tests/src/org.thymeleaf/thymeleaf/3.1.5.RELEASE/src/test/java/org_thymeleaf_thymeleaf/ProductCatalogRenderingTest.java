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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's server-side HTML rendering subsystem for a product catalog page.
public class ProductCatalogRenderingTest {
    @Test
    public void rendersCatalogRowsAndConditionalPromotionForAnApplicationPage() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context();
        context.setVariable("title", "Spring tools");
        context.setVariable("items", List.of(
                new LineItem("Hammer", 2, true),
                new LineItem("Level", 1, false)));

        String rendered = templateEngine.process(CATALOG_TEMPLATE, context);
        String normalized = rendered.replaceAll("\\s+", " ");

        assertTrue(normalized.contains("<title>Spring tools</title>"));
        assertTrue(normalized.contains(
                "<li> <span>Hammer</span> x <span>2</span> <strong>Special offer</strong> </li>"));
        assertTrue(normalized.contains("<li> <span>Level</span> x <span>1</span> </li>"));
        assertFalse(rendered.contains("th:"));
        assertEquals(2, countOccurrences(rendered, "<li>"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private static int countOccurrences(String value, String searched) {
        int count = 0;
        int start = 0;
        while ((start = value.indexOf(searched, start)) >= 0) {
            count++;
            start += searched.length();
        }
        return count;
    }

    private record LineItem(String name, int quantity, boolean promotional) {
    }

    private static final String CATALOG_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <head><title th:text="${title}">Catalog</title></head>
            <body>
            <ul>
            <li th:each="item : ${items}">
              <span th:text="${item.name}">Item</span> x
              <span th:text="${item.quantity}">0</span>
              <strong th:if="${item.promotional}">Special offer</strong>
            </li>
            </ul>
            </body>
            </html>
            """;
}
