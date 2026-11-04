/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.jackson.JsonNodeValueResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' JSON-tree value-resolution subsystem for a catalog page.
public class JsonTreeTemplateTest {
    @Test
    @Timeout(60)
    public void rendersCatalogFromJsonTree() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode catalog = mapper.readTree("""
                {
                  "id": "CAT-7",
                  "title": "Workshop essentials",
                  "featured": true,
                  "items": [
                    {"sku": "WR-42", "name": "Socket wrench", "price": "$29.95"},
                    {"sku": "SG-10", "name": "Safety glasses", "price": "$8.50"}
                  ]
                }
                """);
        Handlebars handlebars = new Handlebars();
        Template template = handlebars.compileInline("""
                <section data-catalog="{{id}}">
                  <h1>{{title}}</h1>
                  {{#if featured}}<strong>Featured</strong>{{/if}}
                  <ul>{{#each items}}<li data-sku="{{sku}}">{{name}}: {{price}}</li>{{/each}}</ul>
                </section>
                """);

        Context context = Context.newBuilder(catalog)
                .resolver(JsonNodeValueResolver.INSTANCE)
                .build();
        String rendered = template.apply(context);

        assertEquals(
                "<section data-catalog=\"CAT-7\"> <h1>Workshop essentials</h1> "
                        + "<strong>Featured</strong> <ul><li data-sku=\"WR-42\">Socket wrench: $29.95</li>"
                        + "<li data-sku=\"SG-10\">Safety glasses: $8.50</li></ul> </section>",
                rendered.replaceAll("\\s+", " ").trim());
    }
}
