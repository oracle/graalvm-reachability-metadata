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
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' template-cache invalidation subsystem for an editable notice.
public class TemplateCacheInvalidationTest {
    @Test
    @Timeout(60)
    public void rendersUpdatedTemplateAfterCacheEviction() throws IOException {
        Path templateDirectory = Files.createTempDirectory("handlebars-cache-");
        Path noticeTemplate = templateDirectory.resolve("notice.hbs");
        Files.writeString(noticeTemplate, "<p class=\"notice\">Hello, {{name}}!</p>");

        try {
            FileTemplateLoader loader = new FileTemplateLoader(templateDirectory.toFile());
            ConcurrentMapTemplateCache cache = new ConcurrentMapTemplateCache();
            Handlebars handlebars = new Handlebars(loader).with(cache);
            Map<String, String> model = Map.of("name", "Mara");

            Template initial = handlebars.compile("notice");
            assertEquals(
                    "<p class=\"notice\">Hello, Mara!</p>",
                    initial.apply(Context.newBuilder(model).build()));

            Files.writeString(noticeTemplate, "<p class=\"notice\">Welcome back, {{name}}.</p>");
            cache.evict(loader.sourceAt("notice"));

            Template refreshed = handlebars.compile("notice");
            assertEquals(
                    "<p class=\"notice\">Welcome back, Mara.</p>",
                    refreshed.apply(Context.newBuilder(model).build()));
        } finally {
            Files.deleteIfExists(noticeTemplate);
            Files.deleteIfExists(templateDirectory);
        }
    }
}
