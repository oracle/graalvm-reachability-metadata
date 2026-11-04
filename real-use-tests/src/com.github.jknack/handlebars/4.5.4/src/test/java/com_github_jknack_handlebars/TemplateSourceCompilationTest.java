/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.StringTemplateSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' template-source compilation subsystem for a notification email.
public class TemplateSourceCompilationTest {
    @Test
    @Timeout(60)
    public void rendersNotificationFromTemplateSource() throws IOException {
        StringTemplateSource source = new StringTemplateSource(
                "notification.hbs",
                "<article>\n"
                        + "  <h1>{{subject}}</h1>\n"
                        + "  <p>Hello, {{recipient}}. The build is {{status}}.</p>\n"
                        + "</article>\n");
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compile(source);

        String rendered = template.apply(Context.newBuilder(Map.of(
                "recipient", "Mara",
                "subject", "Build complete",
                "status", "ready")).build());

        assertEquals(
                "<article> <h1>Build complete</h1> <p>Hello, Mara. The build is ready.</p> </article>",
                rendered.replaceAll("\\s+", " ").trim());
    }
}
