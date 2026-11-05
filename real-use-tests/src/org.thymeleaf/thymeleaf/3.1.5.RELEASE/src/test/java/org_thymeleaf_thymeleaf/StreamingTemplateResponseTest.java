/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf_thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.IThrottledTemplateProcessor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's throttled template processing subsystem for a streamed order response.
public class StreamingTemplateResponseTest {
    @Test
    public void streamsRenderedResponseInBoundedChunks() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context();
        context.setVariable("customer", "Nora");
        context.setVariable("items", List.of("Travel adapter", "Notebook"));

        IThrottledTemplateProcessor processor =
                templateEngine.processThrottled(ORDER_TEMPLATE, context);
        StringWriter response = new StringWriter();
        int processCalls = 0;

        while (!processor.isFinished()) {
            processor.process(1, response);
            processCalls++;
            assertTrue(processCalls <= 1000);
        }

        String rendered = response.toString();

        assertTrue(processCalls > 1);
        assertTrue(rendered.contains("<h1>Order for Nora</h1>"));
        assertTrue(rendered.contains("<li>Travel adapter</li>"));
        assertTrue(rendered.contains("<li>Notebook</li>"));
        assertFalse(rendered.contains("th:"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private static final String ORDER_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <body>
            <h1 th:text="|Order for ${customer}|">Order</h1>
            <ul>
            <li th:each="item : ${items}" th:text="${item}">Item</li>
            </ul>
            </body>
            </html>
            """;
}
