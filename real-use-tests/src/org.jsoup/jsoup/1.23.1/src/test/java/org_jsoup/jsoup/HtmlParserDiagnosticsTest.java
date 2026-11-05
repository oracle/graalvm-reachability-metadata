/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's HTML parser diagnostics subsystem to validate recovered article markup.
 */
public class HtmlParserDiagnosticsTest {

    @Test
    @Timeout(60)
    public void reportsMarkupErrorsAndKeepsRecoveredArticleContent() {
        String submittedMarkup = """
                <html><body>
                <article data-id="trail-42"><h1>Rain on the ridge</h1>
                <p>Pack a <b>dry jacket</p>
                <div class="next">Camp cooking
                """;
        Parser parser = Parser.htmlParser().setTrackErrors(10);

        Document article = parser.parseInput(
                submittedMarkup, "https://journal.example.test/submit");
        List<ParseError> errors = parser.getErrors();

        assertEquals(2, errors.size());
        assertTrue(errors.get(0).getErrorMessage().contains("Unexpected EndTag"));
        assertTrue(errors.get(1).getErrorMessage().contains("Unexpected EOF"));
        assertTrue(errors.get(0).getPosition() < errors.get(1).getPosition());
        assertEquals("Rain on the ridge", article.selectFirst("article h1").text());
        assertEquals("Pack a dry jacket", article.selectFirst("article p").text());
        assertEquals("Camp cooking", article.selectFirst("div.next").text());
    }
}
