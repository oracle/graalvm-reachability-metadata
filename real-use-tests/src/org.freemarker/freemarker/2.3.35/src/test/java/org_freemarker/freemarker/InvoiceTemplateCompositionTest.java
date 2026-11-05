/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.core.Environment;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;
import freemarker.cache.StringTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's template composition and data-model subsystem to render an invoice.
public class InvoiceTemplateCompositionTest {
    @Test
    @Timeout(60)
    public void composesInvoicePartialsAndApplicationDirective() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("partials/header.ftl", "Invoice ${invoice.number}\n"
                + "Customer: ${invoice.customer}\n"
                + "Status: <@status value=invoice.status />\n");
        templates.putTemplate("invoice.ftl", "<#include \"partials/header.ftl\">\n"
                + "Items:\n"
                + "<#list invoice.items as item>${item.quantity} x ${item.name} @ "
                + "${item.price?string[\"0.00\"]} = "
                + "${(item.quantity * item.price)?string[\"0.00\"]}\n</#list>"
                + "Total: ${invoice.total?string[\"0.00\"]}\n");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setLocale(Locale.US);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Map<String, Object> invoice = Map.of(
                "number", "INV-42",
                "customer", "A & B Supplies",
                "status", "ready",
                "items", List.of(
                        Map.of("quantity", 2, "name", "USB-C cable", "price", new BigDecimal("9.50")),
                        Map.of("quantity", 1, "name", "Dock", "price", new BigDecimal("79.00"))),
                "total", new BigDecimal("98.00"));
        Map<String, Object> model = Map.of(
                "invoice", invoice,
                "status", new UppercaseStatusDirective());

        Template template = configuration.getTemplate("invoice.ftl");
        StringWriter rendered = new StringWriter();
        template.process(model, rendered);

        assertEquals("Invoice INV-42\n"
                + "Customer: A & B Supplies\n"
                + "Status: READY\n"
                + "Items:\n"
                + "2 x USB-C cable @ 9.50 = 19.00\n"
                + "1 x Dock @ 79.00 = 79.00\n"
                + "Total: 98.00\n", rendered.toString());
    }

    private static final class UppercaseStatusDirective implements TemplateDirectiveModel {
        @Override
        public void execute(Environment environment, Map params, TemplateModel[] loopVariables,
                TemplateDirectiveBody body) throws TemplateException, IOException {
            Object value = params.get("value");
            if (!(value instanceof TemplateScalarModel scalar)) {
                throw new TemplateModelException("status must be a scalar");
            }
            environment.getOut().write(scalar.getAsString().toUpperCase(Locale.ROOT));
        }
    }
}
