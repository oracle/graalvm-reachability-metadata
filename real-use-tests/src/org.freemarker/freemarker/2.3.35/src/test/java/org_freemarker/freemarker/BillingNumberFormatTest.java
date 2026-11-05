/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_freemarker.freemarker;

import freemarker.cache.StringTemplateLoader;
import freemarker.core.Environment;
import freemarker.core.TemplateNumberFormat;
import freemarker.core.TemplateNumberFormatFactory;
import freemarker.core.TemplateValueFormatException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's custom number-format subsystem to render billing amounts.
public class BillingNumberFormatTest {
    @Test
    @Timeout(60)
    public void rendersApplicationBillingFormat() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("receipt.ftl", "Subtotal: ${subtotal?string[\"@billing\"]}\n"
                + "Tax: ${tax?string[\"@billing\"]}\n"
                + "Total: ${total?string[\"@billing\"]}\n");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setLocale(Locale.US);
        configuration.setTemplateLoader(templates);
        configuration.setCustomNumberFormats(Map.of("billing", new BillingNumberFormatFactory()));
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("receipt.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of(
                "subtotal", new BigDecimal("19.995"),
                "tax", new BigDecimal("1.605"),
                "total", new BigDecimal("21.60")), rendered);

        assertEquals("Subtotal: USD 20.00\n"
                + "Tax: USD 1.61\n"
                + "Total: USD 21.60\n", rendered.toString());
    }

    private static final class BillingNumberFormatFactory extends TemplateNumberFormatFactory {
        @Override
        public TemplateNumberFormat get(String params, Locale locale, Environment environment)
                throws TemplateValueFormatException {
            return new BillingNumberFormat();
        }
    }

    private static final class BillingNumberFormat extends TemplateNumberFormat {
        @Override
        public String getDescription() {
            return "US dollar billing amount";
        }

        @Override
        public String formatToPlainText(TemplateNumberModel number)
                throws TemplateValueFormatException, TemplateModelException {
            BigDecimal amount = new BigDecimal(number.getAsNumber().toString())
                    .setScale(2, RoundingMode.HALF_UP);
            return "USD " + amount.toPlainString();
        }

        @Override
        public boolean isLocaleBound() {
            return false;
        }
    }
}
