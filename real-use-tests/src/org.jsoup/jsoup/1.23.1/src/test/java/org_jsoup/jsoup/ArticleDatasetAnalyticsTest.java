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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Jsoup's data attribute subsystem to prepare an analytics event from a news card.
 */
public class ArticleDatasetAnalyticsTest {

    @Test
    @Timeout(60)
    public void readsAndUpdatesArticleTrackingData() {
        Document page = Jsoup.parse("""
                <!doctype html>
                <html>
                <body>
                <article class="story" data-story-id="ridge-7"
                         data-section="trail notes" data-featured="true">
                  <h2>Rain on the ridge</h2>
                </article>
                </body>
                </html>
                """);

        Element story = page.selectFirst("article.story");
        Map<String, String> trackingData = story.dataset();

        trackingData.put("campaign", "weekly-digest");
        trackingData.remove("featured");
        ArticleEvent event = new ArticleEvent(
                trackingData.get("story-id"),
                trackingData.get("section"),
                trackingData.get("campaign"));

        assertEquals(new ArticleEvent("ridge-7", "trail notes", "weekly-digest"), event);
        assertFalse(trackingData.containsKey("featured"));
        assertTrue(story.hasAttr("data-campaign"));
        assertEquals("weekly-digest", story.attr("data-campaign"));
        assertEquals("Rain on the ridge", story.selectFirst("h2").text());
    }

    private record ArticleEvent(String storyId, String section, String campaign) {
    }
}
