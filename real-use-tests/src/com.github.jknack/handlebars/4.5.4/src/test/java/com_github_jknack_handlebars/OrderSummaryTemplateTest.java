/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.helper.ext.JodaHelper;
import com.github.jknack.handlebars.helper.ext.NumberHelper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' extension-helper subsystem to render a deterministic order summary.
public class OrderSummaryTemplateTest {
    @Test
    @Timeout(60)
    public void rendersOrderSummaryWithApplicationHelpers() throws IOException {
        Handlebars handlebars = new Handlebars();
        StringHelpers.register(handlebars);
        NumberHelper.register(handlebars);
        handlebars.registerHelper(JodaHelper.jodaPattern.name(), JodaHelper.jodaPattern);
        handlebars.registerHelper("money", new MoneyHelper());

        String source = """
                <article data-order=\"{{id}}\">
                  <h1>{{title}}</h1>
                  <p>Placed {{jodaPattern placedAt \"yyyy-MM-dd\"}}.</p>
                  <ul>
                  {{#each lines}}    <li class=\"{{stripes quantity \"even\" \"odd\"}}\">{{name}} x {{quantity}}: {{money amount}}</li>
                  {{/each}}</ul>
                </article>
                """;
        Template template = handlebars.compileInline(source);
        Map<String, Object> order = Map.of(
                "id", "ORD-42",
                "title", "Workshop supplies",
                "placedAt", new DateTime(2025, 2, 3, 14, 45, DateTimeZone.UTC),
                "lines", List.of(
                        Map.of("name", "Socket wrench", "quantity", 2, "amount", new BigDecimal("29.95")),
                        Map.of("name", "Safety glasses", "quantity", 1, "amount", new BigDecimal("8.50"))));

        String rendered = template.apply(Context.newBuilder(order).build()).strip();

        assertEquals(
                "<article data-order=\"ORD-42\"> <h1>Workshop supplies</h1> "
                        + "<p>Placed 2025-02-03.</p> <ul> "
                        + "<li class=\"even\">Socket wrench x 2: $29.95</li> "
                        + "<li class=\"odd\">Safety glasses x 1: $8.50</li> </ul> </article>",
                rendered.replaceAll("\\s+", " ").trim());
    }

    private static final class MoneyHelper implements Helper<BigDecimal> {
        @Override
        public Object apply(BigDecimal amount, Options options) {
            return "$" + amount.setScale(2).toPlainString();
        }
    }
}
