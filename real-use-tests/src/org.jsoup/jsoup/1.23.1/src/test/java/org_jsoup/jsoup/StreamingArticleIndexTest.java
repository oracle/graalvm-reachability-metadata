/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jsoup.jsoup;

import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.StreamParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Jsoup's streaming parser subsystem to incrementally index article cards for a news application.
 */
public class StreamingArticleIndexTest {

    @Test
    @Timeout(60)
    public void indexesArticlesAsTheyArrive() {
        String newsPage = """
                <!doctype html>
                <html>
                <body>
                <main id="latest">
                  <article class="story" data-id="ridge-7">
                    <h2>Rain on the ridge</h2>
                    <p class="summary">A short route update.</p>
                    <a class="permalink" href="/stories/ridge-7">Read story</a>
                  </article>
                  <aside>Subscribe for weekly trail notes.</aside>
                  <article class="story" data-id="cooking-8">
                    <h2>Camp cooking</h2>
                    <p class="summary">Keep the kettle near the fire.</p>
                    <a class="permalink" href="/stories/cooking-8">Read story</a>
                  </article>
                </main>
                </body>
                </html>
                """;
        List<StorySummary> stories = new ArrayList<>();

        try (StreamParser streamParser = new StreamParser(Parser.htmlParser())
                .parse(newsPage, "https://journal.example.test/")) {
            streamParser.stream()
                    .filter(element -> element.is("article.story"))
                    .map(StreamingArticleIndexTest::summarize)
                    .forEach(stories::add);
        }

        assertEquals(List.of(
                new StorySummary("ridge-7", "Rain on the ridge",
                        "A short route update.",
                        "https://journal.example.test/stories/ridge-7"),
                new StorySummary("cooking-8", "Camp cooking",
                        "Keep the kettle near the fire.",
                        "https://journal.example.test/stories/cooking-8")), stories);
    }

    private static StorySummary summarize(Element story) {
        return new StorySummary(
                story.attr("data-id"),
                story.selectFirst("h2").text(),
                story.selectFirst("p.summary").text(),
                story.selectFirst("a.permalink").absUrl("href"));
    }

    private record StorySummary(String id, String title, String summary, String link) {
    }
}
