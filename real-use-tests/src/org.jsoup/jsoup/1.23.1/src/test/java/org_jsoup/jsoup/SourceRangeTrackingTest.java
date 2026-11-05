/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's source-range tracking subsystem to locate article fields in an editor submission.
 */
public class SourceRangeTrackingTest {

    @Test
    @Timeout(60)
    public void locatesArticleFieldsInSubmittedMarkup() {
        String submittedMarkup = "<article data-id=\"ridge-7\">\n"
                + "  <h1>Rain on the ridge</h1>\n"
                + "  <p class=\"summary\">A short route update.</p>\n"
                + "</article>";
        Parser parser = Parser.htmlParser().setTrackPosition(true);

        Document submission = parser.parseInput(
                submittedMarkup, "https://journal.example.test/editor");
        Element article = submission.selectFirst("article");
        Element title = article.selectFirst("h1");
        Attribute articleId = article.attribute("data-id");
        Range titleStart = title.sourceRange();
        Range titleEnd = title.endSourceRange();
        Range.AttributeRange idRange = articleId.sourceRange();

        assertEquals("Rain on the ridge", title.text());
        assertTrue(titleStart.isTracked());
        assertEquals(2, titleStart.start().lineNumber());
        assertEquals(3, titleStart.start().columnNumber());
        assertEquals(30, titleStart.startPos());
        assertEquals(34, titleStart.endPos());
        assertEquals(51, titleEnd.startPos());
        assertEquals(56, titleEnd.endPos());
        assertEquals(9, idRange.nameRange().startPos());
        assertEquals(16, idRange.nameRange().endPos());
        assertEquals(18, idRange.valueRange().startPos());
        assertEquals(25, idRange.valueRange().endPos());
        assertEquals("ridge-7", articleId.getValue());
    }
}
