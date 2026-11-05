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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's auto-import subsystem to apply a shared storefront component.
public class AutoImportTemplateTest {
    @Test
    @Timeout(60)
    public void appliesSharedBadgeToProductPageWithoutExplicitImport() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("shared/storefront.ftl",
                "<#macro badge label>${label?upper_case}</#macro>");
        templates.putTemplate("products.ftl",
                "Product: ${name} [<@storefront.badge label=availability />]");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setSetting("auto_import", "shared/storefront.ftl as storefront");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("products.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of("name", "Travel mug", "availability", "in stock"), rendered);

        assertEquals("Product: Travel mug [IN STOCK]", rendered.toString());
    }
}
