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
import com.github.jknack.handlebars.io.FileTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' file template-loading subsystem for a server-rendered receipt.
public class FileTemplateLoaderTest {
    @Test
    @Timeout(60)
    public void loadsReceiptTemplateAndPartialFromFiles() throws IOException {
        Path templateDirectory = Files.createTempDirectory("handlebars-templates-");
        Files.writeString(
                templateDirectory.resolve("receipt.hbs"),
                """
                <section class="receipt">
                  <h2>{{customer}}</h2>
                  <ul>
                    {{#each items}}{{> item}}{{/each}}
                  </ul>
                </section>
                """);
        Files.writeString(
                templateDirectory.resolve("item.hbs"),
                "<li data-sku=\"{{sku}}\">{{name}}: {{price}}</li>\n");

        try {
            Handlebars handlebars = new Handlebars(new FileTemplateLoader(templateDirectory.toFile()));
            Template template = handlebars.compile("receipt");
            Map<String, Object> receipt = Map.of(
                    "customer", "Mara & Co.",
                    "items", List.of(
                            Map.of("sku", "WR-42", "name", "Socket wrench", "price", "$29.95"),
                            Map.of("sku", "SG-10", "name", "Safety glasses", "price", "$8.50")));

            String rendered = template.apply(Context.newBuilder(receipt).build());

            assertEquals(
                    "<section class=\"receipt\"> <h2>Mara &amp; Co.</h2> "
                            + "<ul> <li data-sku=\"WR-42\">Socket wrench: $29.95</li> "
                            + "<li data-sku=\"SG-10\">Safety glasses: $8.50</li> </ul> </section>",
                    rendered.replaceAll("\\s+", " ").trim());
        } finally {
            Files.deleteIfExists(templateDirectory.resolve("item.hbs"));
            Files.deleteIfExists(templateDirectory.resolve("receipt.hbs"));
            Files.deleteIfExists(templateDirectory);
        }
    }
}
