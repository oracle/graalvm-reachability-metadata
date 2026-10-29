/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdiff_tests;

import jdiff.API;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class APITest {
    @Test
    void wrapsHtmlDocumentationAsCdata() {
        String html = "<p>Call <code>lookup()</code> & inspect the result.</p>";

        assertEquals("<![CDATA[" + html + "]]>", API.stuffHTMLTags(html));
    }

    @Test
    void hidesAndRestoresTagsAndEntitiesInDocumentation() {
        String html = "<p>Read <b>all</b> entries &amp; continue.</p>";

        String hidden = API.hideHTMLTags(html);

        assertFalse(hidden.contains("<"));
        assertFalse(hidden.contains("&"));
        assertEquals(html, API.showHTMLTags(hidden));
    }

    @Test
    void preservesPlainAndEmptyDocumentation() {
        assertEquals("Plain documentation.", API.showHTMLTags(API.hideHTMLTags("Plain documentation.")));
        assertEquals("", API.showHTMLTags(API.hideHTMLTags("")));
        assertEquals("<![CDATA[]]>", API.stuffHTMLTags(""));
    }
}
