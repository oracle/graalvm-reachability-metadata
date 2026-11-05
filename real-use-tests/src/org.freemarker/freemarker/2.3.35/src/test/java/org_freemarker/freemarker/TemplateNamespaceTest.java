/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's template namespace subsystem to render a reusable order summary.
public class TemplateNamespaceTest {
    @Test
    @Timeout(60)
    public void rendersOrdersThroughImportedTemplateMacros() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("order-macros.ftl", "<#macro row order>${order.id}: ${order.total?string[\"0.00\"]}\\n</#macro>");
        templates.putTemplate("orders.ftl", "<#import \"order-macros.ftl\" as orderView>"
                + "<h1>${title}</h1>\\n"
                + "<#list orders as order><@orderView.row order=order /></#list>");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("orders.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of(
                "title", "Today's orders",
                "orders", List.of(
                        Map.of("id", "ORD-100", "total", 125.5),
                        Map.of("id", "ORD-101", "total", 79.0))), rendered);

        assertEquals("<h1>Today's orders</h1>\\n"
                + "ORD-100: 125.50\\n"
                + "ORD-101: 79.00\\n", rendered.toString());
    }
}
