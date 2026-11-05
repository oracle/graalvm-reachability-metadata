/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.SimpleScalar;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's custom method-model subsystem to calculate shipping quotes.
public class ShippingQuoteMethodTest {
    @Test
    @Timeout(60)
    public void rendersShippingQuotesForFulfillmentOrders() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("shipping.ftl", "<#list orders as order>"
                + "${order.id}: ${shippingQuote(order.destination, order.weight)}\n"
                + "</#list>");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setSharedVariable("shippingQuote", new ShippingQuoteMethod());
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("shipping.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of("orders", List.of(
                Map.of("id", "ORD-100", "destination", "US", "weight", 2),
                Map.of("id", "ORD-101", "destination", "CA", "weight", 5))), rendered);

        assertEquals("ORD-100: USD 8.00\nORD-101: CAD 20.00\n", rendered.toString());
    }

    private static final class ShippingQuoteMethod implements TemplateMethodModelEx {
        @Override
        public Object exec(List arguments) throws TemplateModelException {
            if (arguments.size() != 2
                    || !(arguments.get(0) instanceof TemplateScalarModel destination)
                    || !(arguments.get(1) instanceof TemplateNumberModel weight)) {
                throw new TemplateModelException("shippingQuote expects destination and weight");
            }
            String currency = "CA".equals(destination.getAsString()) ? "CAD" : "USD";
            double amount = 4.0 * weight.getAsNumber().doubleValue();
            return new SimpleScalar(String.format(Locale.ROOT, "%s %.2f", currency, amount));
        }
    }
}
