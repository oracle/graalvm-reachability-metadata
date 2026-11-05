/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.cache.StringTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's JavaBeans object-wrapping subsystem to render order summaries.
public class BeanModelRenderingTest {
    @Test
    @Timeout(60)
    public void rendersBeanPropertiesFromApplicationOrders() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("orders.ftl", "<#list orders as order>"
                + "${order.reference}: ${order.customerName} - ${order.total?string[\"0.00\"]}\n"
                + "</#list>");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setObjectWrapper(new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_35).build());
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("orders.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of("orders", List.of(
                new OrderSummary("ORD-100", "Northwind", 125.50),
                new OrderSummary("ORD-101", "Contoso", 79.00))), rendered);

        assertEquals("ORD-100: Northwind - 125.50\n"
                + "ORD-101: Contoso - 79.00\n", rendered.toString());
    }

    public static final class OrderSummary {
        private final String reference;
        private final String customerName;
        private final double total;

        public OrderSummary(String reference, String customerName, double total) {
            this.reference = reference;
            this.customerName = customerName;
            this.total = total;
        }

        public String getReference() {
            return reference;
        }

        public String getCustomerName() {
            return customerName;
        }

        public double getTotal() {
            return total;
        }
    }
}
