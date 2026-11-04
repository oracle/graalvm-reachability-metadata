/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Lambda;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' lambda subsystem to format a notification status supplied by an application.
public class LambdaNotificationTest {
    @Test
    @Timeout(60)
    public void formatsStatusThroughContextLambda() throws IOException {
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline(
                "<p class=\"notification\">{{recipient}}: "
                        + "{{#statusBadge}}{{status}}{{/statusBadge}}</p>");

        Map<String, Object> model = new HashMap<>();
        model.put("recipient", "Mara");
        model.put("status", "ready");
        model.put("statusBadge", (Lambda<Object, Object>) (context, body) ->
                "<strong>" + body.apply(context).toUpperCase(Locale.ROOT) + "</strong>");

        assertEquals(
                "<p class=\"notification\">Mara: <strong>READY</strong></p>",
                template.apply(model));
    }
}
