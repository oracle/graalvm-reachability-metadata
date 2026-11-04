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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' inline-partial subsystem for a reusable order-line component.
public class InlinePartialOrderTest {
    @Test
    @Timeout(60)
    public void rendersOrderLinesThroughAnInlinePartial() throws IOException {
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline(
                "{{#*inline \"line\"}}"
                        + "<li data-sku=\"{{sku}}\">{{name}} x {{quantity}}</li>"
                        + "{{/inline}}"
                        + "<ul>{{#each lines}}{{> line}}{{/each}}</ul>");
        Map<String, Object> order = Map.of(
                "lines", List.of(
                        Map.of("sku", "KB-7", "name", "Keyboard", "quantity", 2),
                        Map.of("sku", "MS-3", "name", "Mouse", "quantity", 1)));

        String rendered = template.apply(Context.newBuilder(order).build());

        assertEquals(
                "<ul><li data-sku=\"KB-7\">Keyboard x 2</li>"
                        + "<li data-sku=\"MS-3\">Mouse x 1</li></ul>",
                rendered);
    }
}
