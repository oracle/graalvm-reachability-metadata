/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Web-safe JSON output: default HTML escaping, `disableHtmlEscaping`, the escaping of line and
 * paragraph separators, and the non-executable `)]}'` prefix that guards JSON endpoints against
 * cross-site script inclusion.
 *
 * A storefront renders product pages on the server and embeds the page state as JSON inside a
 * `<script>` element, so user-written review text must not be able to close that element. The
 * same storefront serves its review API with the anti-XSSI prefix that its own Gson client strips,
 * and exports reviews to a plain-text moderation log where escaping would only obscure the text.
 */
public class StorefrontPageWebSafeOutputTest {

    private static final String HOSTILE_REVIEW =
            "</script><script>alert('pwned')</script> Tom & Jerry say 2 > 1 = true";

    private static final Gson PAGE_GSON = new Gson();

    @Test
    void embeddedPageStateCannotBreakOutOfTheScriptElement() {
        PageState state = new PageState("sku-42", "Garden <Deluxe> Hose",
                List.of(new Review("mallory", 1, HOSTILE_REVIEW)));

        String json = PAGE_GSON.toJson(state);
        for (String unsafe : List.of("<", ">", "&", "=", "'")) {
            assertFalse(json.contains(unsafe), () -> "raw " + unsafe + " leaked into " + json);
        }
        assertTrue(json.contains("\\u003c/script\\u003e"));
        assertTrue(json.contains("Tom \\u0026 Jerry"));
        assertTrue(json.contains("alert(\\u0027pwned\\u0027)"));
        assertTrue(json.contains("1 \\u003d true"));

        String page = PageRenderer.render(state, PAGE_GSON);
        String scriptBody = PageRenderer.extractStateScript(page);
        assertEquals(json, scriptBody, "the first </script> in the page is the renderer's own");

        PageState restored = PAGE_GSON.fromJson(scriptBody, PageState.class);
        assertEquals(state, restored);
    }

    @Test
    void lineAndParagraphSeparatorsAreEscapedEvenWithoutHtmlEscaping() {
        String review = "first line\u2028second line\u2029next paragraph";
        Gson plainGson = new GsonBuilder().disableHtmlEscaping().create();

        for (Gson gson : List.of(PAGE_GSON, plainGson)) {
            String json = gson.toJson(new Review("ann", 5, review));
            assertFalse(json.contains("\u2028"));
            assertFalse(json.contains("\u2029"));
            assertTrue(json.contains("first line\\u2028second line\\u2029next paragraph"));
            assertEquals(review, gson.fromJson(json, Review.class).text());
        }
    }

    @Test
    void moderationLogKeepsReviewTextReadableWhenEscapingIsDisabled() {
        Gson logGson = new GsonBuilder().disableHtmlEscaping().create();
        Review review = new Review("mallory", 1, HOSTILE_REVIEW);

        String line = logGson.toJson(review);

        assertEquals("""
                {"author":"mallory","stars":1,"text":"</script><script>alert('pwned')</script> \
                Tom & Jerry say 2 > 1 = true"}""", line);
        assertEquals(review, logGson.fromJson(line, Review.class));
        assertEquals(review, PAGE_GSON.fromJson(line, Review.class),
                "escaping is an output choice; both forms read back to the same review");
    }

    @Test
    void treeToStringIsUnescapedWhileGsonOutputOfTheSameTreeIsEscaped() {
        JsonObject event = new JsonObject();
        event.addProperty("<b>label</b>", "Q&A");

        assertEquals("{\"<b>label</b>\":\"Q&A\"}", event.toString());
        assertEquals("{\"\\u003cb\\u003elabel\\u003c/b\\u003e\":\"Q\\u0026A\"}",
                PAGE_GSON.toJson(event));
    }

    @Test
    void handWrittenStreamHonorsTheGsonHtmlSafetySetting() throws IOException {
        StringWriter escaped = new StringWriter();
        try (JsonWriter writer = PAGE_GSON.newJsonWriter(escaped)) {
            assertTrue(writer.isHtmlSafe());
            writer.beginObject().name("query").value("a<b&c").endObject();
        }
        assertEquals("{\"query\":\"a\\u003cb\\u0026c\"}", escaped.toString());

        Gson plainGson = new GsonBuilder().disableHtmlEscaping().create();
        StringWriter raw = new StringWriter();
        try (JsonWriter writer = plainGson.newJsonWriter(raw)) {
            assertFalse(writer.isHtmlSafe());
            writer.beginObject().name("query").value("a<b&c").endObject();
        }
        assertEquals("{\"query\":\"a<b&c\"}", raw.toString());
    }

    @Test
    void reviewApiPrefixesResponsesThatTheGsonClientStrips() {
        Gson apiGson = new GsonBuilder().generateNonExecutableJson().create();
        ReviewPage page = new ReviewPage("sku-42", 2,
                List.of(new Review("ann", 5, "Works great"), new Review("bob", 4, "Solid & cheap")));

        String body = apiGson.toJson(page);

        assertTrue(body.startsWith(")]}'\n"), body);
        String payload = body.substring(")]}'\n".length());
        assertEquals(page, PAGE_GSON.fromJson(payload, ReviewPage.class));
        assertTrue(payload.contains("Solid \\u0026 cheap"));

        assertEquals(page, PAGE_GSON.fromJson(body, ReviewPage.class),
                "the default client reads leniently and skips the prefix");
        assertEquals(page, apiGson.fromJson(body, ReviewPage.class));

        Gson strictClient = new GsonBuilder().setStrictness(Strictness.STRICT).create();
        assertThrows(JsonSyntaxException.class, () -> strictClient.fromJson(body, ReviewPage.class));
        assertEquals(page, strictClient.fromJson(payload, ReviewPage.class));
    }

    /// Renders a product page with its state embedded in a script element.
    static final class PageRenderer {

        private static final String STATE_OPEN = "<script id=\"page-state\" type=\"application/json\">";
        private static final String SCRIPT_CLOSE = "</script>";

        private PageRenderer() {
        }

        static String render(PageState state, Gson gson) {
            return """
                    <!doctype html>
                    <html><head><title>%s</title></head>
                    <body>
                    %s%s%s
                    <script src="/app.js"></script>
                    </body></html>
                    """.formatted(state.sku(), STATE_OPEN, gson.toJson(state), SCRIPT_CLOSE);
        }

        /// Extracts the state script body the way an HTML parser does: up to the first `</script>`.
        static String extractStateScript(String page) {
            int start = page.indexOf(STATE_OPEN);
            assertTrue(start >= 0, "page has no state script");
            int bodyStart = start + STATE_OPEN.length();
            return page.substring(bodyStart, page.indexOf(SCRIPT_CLOSE, bodyStart));
        }
    }

    record Review(String author, int stars, String text) {
    }

    record PageState(String sku, String title, List<Review> reviews) {
    }

    record ReviewPage(String sku, int total, List<Review> reviews) {
    }
}
