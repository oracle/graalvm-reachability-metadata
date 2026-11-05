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
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises FreeMarker's date and time formatting subsystem for an event schedule.
public class DateTimeFormattingTest {
    @Test
    @Timeout(60)
    public void formatsEventTimesInConfiguredTimeZone() throws Exception {
        StringTemplateLoader templates = new StringTemplateLoader();
        templates.putTemplate("event.ftl", "${event.name} starts ${event.starts?string(\"yyyy-MM-dd HH:mm z\")}\n"
                + "Ends ${event.ends?string(\"HH:mm z\")}");

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_35);
        configuration.setLocale(Locale.US);
        configuration.setTimeZone(TimeZone.getTimeZone("UTC"));
        configuration.setTemplateLoader(templates);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);

        Template template = configuration.getTemplate("event.ftl");
        StringWriter rendered = new StringWriter();
        template.process(Map.of("event", Map.of(
                "name", "Launch briefing",
                "starts", Date.from(Instant.parse("2025-03-14T09:30:00Z")),
                "ends", Date.from(Instant.parse("2025-03-14T10:45:00Z")))), rendered);

        assertEquals("Launch briefing starts 2025-03-14 09:30 UTC\nEnds 10:45 UTC", rendered.toString());
    }
}
