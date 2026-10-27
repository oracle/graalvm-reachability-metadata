/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Gson's HTML-safe JSON serialization to embed user-supplied saved searches in a web page.
public class SavedSearchHtmlEmbeddingTest {

    @Test
    @Timeout(60)
    public void embedsSearchSettingsWithoutLettingUserContentCloseTheScriptElement() {
        Gson gson = new Gson();
        SavedSearch search = new SavedSearch();
        search.title = "Ada's <Books> & Gifts";
        search.query = "</script><img src=x onerror='alert(1)'> \"sale\"\nbooks";
        search.tags = List.of("fiction & essays", "price < 20", "edition=first");
        search.pageSize = 25;

        String page = renderPage(gson, search);
        String scriptOpening = "<script id=\"saved-search\" type=\"application/json\">";
        int payloadStart = page.indexOf(scriptOpening) + scriptOpening.length();
        int payloadEnd = page.indexOf("</script>", payloadStart);

        assertTrue(page.contains(scriptOpening));
        assertEquals(page.lastIndexOf("</script>"), payloadEnd);
        assertFalse(page.contains("<img"));
        assertFalse(page.contains("<Books>"));

        // A browser reads textContent without decoding HTML entities inside a script element.
        String embeddedJson = page.substring(payloadStart, payloadEnd);
        assertFalse(embeddedJson.contains("<"));
        assertFalse(embeddedJson.contains(">"));
        assertFalse(embeddedJson.contains("&"));
        assertTrue(embeddedJson.contains("\\u003c/script\\u003e"));
        assertTrue(embeddedJson.contains("\\u0026"));

        SavedSearch hydrated = gson.fromJson(embeddedJson, SavedSearch.class);
        assertEquals("Ada's <Books> & Gifts", hydrated.title);
        assertEquals("</script><img src=x onerror='alert(1)'> \"sale\"\nbooks", hydrated.query);
        assertEquals(List.of("fiction & essays", "price < 20", "edition=first"), hydrated.tags);
        assertEquals(25, hydrated.pageSize);
    }

    private static String renderPage(Gson gson, SavedSearch search) {
        return """
                <!doctype html>
                <html><body>
                <script id="saved-search" type="application/json">%s</script>
                </body></html>
                """.formatted(gson.toJson(search));
    }

    public static class SavedSearch {
        private String title;
        private String query;
        private List<String> tags;
        private int pageSize;
    }
}
