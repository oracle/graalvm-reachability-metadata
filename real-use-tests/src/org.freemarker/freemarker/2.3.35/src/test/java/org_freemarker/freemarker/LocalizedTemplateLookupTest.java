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
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's localized template lookup subsystem to render a regional order confirmation.
public class LocalizedTemplateLookupTest {
    @Test
    @Timeout(60)
    public void rendersRegionalOrderConfirmation() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("orders/confirmation.ftl", "Order ${order.id} is ready.\n");
        templates.putTemplate("orders/confirmation_fr.ftl", "La commande ${order.id} est prete.\n");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("orders/confirmation.ftl", Locale.CANADA_FRENCH);
        StringWriter rendered = new StringWriter();
        template.process(Map.of("order", Map.of("id", "ORD-2048")), rendered);

        assertEquals("La commande ORD-2048 est prete.\n", rendered.toString());
    }
}
