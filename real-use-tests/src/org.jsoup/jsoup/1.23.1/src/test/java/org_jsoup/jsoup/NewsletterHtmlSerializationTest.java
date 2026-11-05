/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's HTML serialization subsystem to render a compact XHTML newsletter.
 */
public class NewsletterHtmlSerializationTest {

    @Test
    @Timeout(60)
    public void rendersCompactXhtmlNewsletter() {
        Document newsletter = Jsoup.parse(
                "<main class=\"newsletter\" data-campaign=\"trail &amp; tide\">"
                        + "<h1>Ridge Caf\u00e9</h1>"
                        + "<p>Pack light &amp; stay dry.</p>"
                        + "<img src=\"/logo.png\"></main>");
        Element main = newsletter.selectFirst("main.newsletter");
        main.selectFirst("p").appendText(" Read the full route guide.");
        newsletter.outputSettings(new Document.OutputSettings()
                .charset(StandardCharsets.UTF_8)
                .escapeMode(Entities.EscapeMode.xhtml)
                .syntax(Document.OutputSettings.Syntax.xml)
                .prettyPrint(false));

        String renderedNewsletter = newsletter.outerHtml();

        assertEquals(
                "<html><head></head><body><main class=\"newsletter\" "
                        + "data-campaign=\"trail &amp; tide\"><h1>Ridge Caf\u00e9</h1>"
                        + "<p>Pack light &amp; stay dry. Read the full route guide.</p>"
                        + "<img src=\"/logo.png\" /></main></body></html>",
                renderedNewsletter);
        assertTrue(renderedNewsletter.contains("<img src=\"/logo.png\" />"));
        assertTrue(renderedNewsletter.contains("Ridge Caf\u00e9"));
    }
}
