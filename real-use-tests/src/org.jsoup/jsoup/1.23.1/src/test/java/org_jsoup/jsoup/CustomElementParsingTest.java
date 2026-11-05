/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.ParseSettings;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.parser.TagSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's custom-element parsing subsystem to ingest web-component content for a publishing application.
 */
public class CustomElementParsingTest {

    @Test
    @Timeout(60)
    public void preservesPublishedWebComponentMarkup() {
        Tag storyCard = Tag.valueOf("story-card").set(Tag.Block);
        Parser parser = Parser.htmlParser()
                .tagSet(TagSet.Html().add(storyCard))
                .settings(ParseSettings.preserveCase);
        Document page = parser.parseInput("""
                <story-card DATA-ID="ridge-7">
                  <StoryTitle>Rain on the ridge</StoryTitle>
                  <slot name="summary">The route is open after the storm.</slot>
                </story-card>
                """, "https://journal.example.test/posts/7");

        Element card = page.selectFirst("story-card");
        Element title = card.selectFirst("StoryTitle");
        Element summary = card.selectFirst("slot[name=summary]");

        assertEquals("ridge-7", card.attr("DATA-ID"));
        assertEquals("StoryTitle", title.tagName());
        assertEquals("Rain on the ridge", title.text());
        assertEquals("The route is open after the storm.", summary.text());
        assertTrue(card.tag().is(Tag.Block));
        assertTrue(card.outerHtml().contains("<StoryTitle>Rain on the ridge</StoryTitle>"));
    }
}
