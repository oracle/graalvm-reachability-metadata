/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.runtime.ErrorPageAction;
import io.quarkus.runtime.TemplateHtmlBuilder;

public class TemplateHtmlBuilderTest {
    @Test
    void rendersStyledResourcePageWithEscapedContent() {
        String html = new TemplateHtmlBuilder("http://localhost:8080/", "Routes <catalog>",
                "Available & active", "Select <a> route",
                List.of(new ErrorPageAction("Home", "/")))
                .resourcesStart("Available endpoints")
                .resourcePath("/items/<id>")
                .method("GET", "/items/<id>")
                .produces("application/json")
                .methodEnd()
                .resourceEnd()
                .staticResourcePath("/index.html", "Landing page")
                .resourcesEnd()
                .toString();

        assertThat(html).contains(
                "<title>Routes &lt;catalog&gt; - Available &amp; active</title>",
                "Select &lt;a&gt; route", "<a href=/>Home</a>",
                "<style>html, body {", ".exception-message {",
                "href='http://localhost:8080/items/&lt;id&gt;'",
                "Produces: application/json", "href=\"http://localhost:8080/index.html\"",
                "Landing page").endsWith("</html>\n");
        assertThat(html).doesNotContain("<style>null</style>", "Select <a> route");
    }

    @Test
    void rendersOriginalAndRootCauseFirstStackTraces() {
        IllegalArgumentException cause = new IllegalArgumentException("Invalid <item>");
        IllegalStateException failure = new IllegalStateException("Request & processing", cause);
        StackTraceElement[] originalTrace = failure.getStackTrace();

        String html = new TemplateHtmlBuilder("Request failed", null, "Please retry")
                .stack(failure)
                .toString();

        assertThat(html).contains("id=\"original-stacktrace\"", "id=\"reversed-stacktrace\"",
                "Invalid &lt;item&gt;", "Request &amp; processing", "Caused by:", "Resulted in:");
        assertThat(failure.getStackTrace()).containsExactly(originalTrace);
        assertThat(failure.getCause()).isSameAs(cause);
    }
}
