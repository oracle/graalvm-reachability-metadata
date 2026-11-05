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
import freemarker.template.TemplateTransformModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's template transform subsystem to format an outbound notification.
public class NotificationTransformTest {
    @Test
    @Timeout(60)
    public void formatsNotificationThroughApplicationTransform() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("notification.ftl", "<@quote>Order ${order.id} is ready for ${order.customer}.</@quote>");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("notification.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of(
                "order", Map.of("id", "ORD-42", "customer", "A & B Supplies"),
                "quote", new QuoteTransform()), rendered);

        assertEquals("\"Order ORD-42 is ready for A & B Supplies.\"", rendered.toString());
    }

    private static final class QuoteTransform implements TemplateTransformModel {
        @Override
        public Writer getWriter(Writer output, Map arguments) {
            return new Writer() {
                private final StringWriter captured = new StringWriter();

                @Override
                public void write(char[] characters, int offset, int length) {
                    captured.write(characters, offset, length);
                }

                @Override
                public void write(String text) {
                    captured.write(text, 0, text.length());
                }

                @Override
                public void write(int character) {
                    captured.write(character);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() throws IOException {
                    output.write('"');
                    output.write(captured.toString());
                    output.write('"');
                }
            };
        }
    }
}
