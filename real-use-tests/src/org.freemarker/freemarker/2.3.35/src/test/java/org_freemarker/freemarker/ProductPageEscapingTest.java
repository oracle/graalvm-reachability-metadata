/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.StringTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's HTML output-format subsystem to render a safe product page.
public class ProductPageEscapingTest {
    @Test
    @Timeout(60)
    public void escapesProductContentAndPreservesTrustedMarkup() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("product.ftlh", "<article data-sku=\"${product.sku}\">"
                + "<h1>${product.name}</h1>"
                + "<p>${product.description}</p>"
                + "${product.badge?no_esc}</article>");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("product.ftlh");
        StringWriter rendered = new StringWriter();
        template.process(Map.of("product", Map.of(
                "sku", "desk\"-42",
                "name", "Desk & <Lamp>",
                "description", "5\" arm for A & B",
                "badge", "<strong>In stock</strong>")), rendered);

        assertEquals("<article data-sku=\"desk&quot;-42\"><h1>Desk &amp; &lt;Lamp&gt;</h1>"
                + "<p>5&quot; arm for A &amp; B</p><strong>In stock</strong></article>",
                rendered.toString());
    }
}
