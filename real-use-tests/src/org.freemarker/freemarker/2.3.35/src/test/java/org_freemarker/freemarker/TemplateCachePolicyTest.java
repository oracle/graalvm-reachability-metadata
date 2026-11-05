/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.MruCacheStorage;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's template-cache subsystem for a bounded storefront template cache.
public class TemplateCachePolicyTest {
    @Test
    @Timeout(60)
    public void rendersPagesAfterTemplateCacheEviction() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("catalog.ftl", "Catalog for ${store}");
        templates.putTemplate("checkout.ftl", "Checkout for ${store}");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setCacheStorage(new MruCacheStorage(1, 0));
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template catalog = configuration.getTemplate("catalog.ftl");
        Template checkout = configuration.getTemplate("checkout.ftl");
        Template catalogAfterEviction = configuration.getTemplate("catalog.ftl");

        assertEquals("Catalog for Acme", render(catalog, "Acme"));
        assertEquals("Checkout for Acme", render(checkout, "Acme"));
        assertEquals("Catalog for Acme", render(catalogAfterEviction, "Acme"));
    }

    private static String render(Template template, String store) throws Exception {
        StringWriter rendered = new StringWriter();
        template.process(Map.of("store", store), rendered);
        return rendered.toString();
    }
}
