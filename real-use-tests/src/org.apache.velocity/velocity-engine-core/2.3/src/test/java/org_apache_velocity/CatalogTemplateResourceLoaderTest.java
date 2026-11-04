/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import org.apache.velocity.util.ExtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Velocity's custom resource loader subsystem for tenant template catalogs.
public class CatalogTemplateResourceLoaderTest {
    @Test
    @Timeout(60)
    public void rendersTemplateAndParsedFragmentFromApplicationCatalog() {
        CatalogResourceLoader loader = new CatalogResourceLoader(Map.of(
                "invoice.vm", "Invoice $invoice.number\n#parse(\"fragments/line.vm\")",
                "fragments/line.vm", "Line: $invoice.description x $invoice.quantity"));
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty("resource.loader", "catalog");
        engine.setProperty("catalog.resource.loader.instance", loader);
        engine.init();

        assertTrue(engine.resourceExists("invoice.vm"));
        VelocityContext context = new VelocityContext();
        context.put("invoice", new Invoice("INV-11848", "Replacement filter", 3));
        StringWriter output = new StringWriter();

        engine.getTemplate("invoice.vm", "UTF-8").merge(context, output);

        assertEquals("Invoice INV-11848\nLine: Replacement filter x 3", output.toString());
    }

    public static final class CatalogResourceLoader extends ResourceLoader {
        private final Map<String, String> templates;

        public CatalogResourceLoader(Map<String, String> templates) {
            this.templates = templates;
        }

        @Override
        public void init(ExtProperties configuration) {
            // The catalog is supplied by the application when it creates the loader.
        }

        @Override
        public Reader getResourceReader(String resourceName, String encoding)
                throws ResourceNotFoundException {
            String template = templates.get(resourceName);
            if (template == null) {
                throw new ResourceNotFoundException("Template not found: " + resourceName);
            }
            return new StringReader(template);
        }

        @Override
        public boolean isSourceModified(Resource resource) {
            return false;
        }

        @Override
        public long getLastModified(Resource resource) {
            return 0L;
        }
    }

    public static final class Invoice {
        private final String number;
        private final String description;
        private final int quantity;

        public Invoice(String number, String description, int quantity) {
            this.number = number;
            this.description = description;
            this.quantity = quantity;
        }

        public String getNumber() {
            return number;
        }

        public String getDescription() {
            return description;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
